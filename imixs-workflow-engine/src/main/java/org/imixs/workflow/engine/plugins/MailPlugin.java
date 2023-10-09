/*  
 *  Imixs-Workflow 
 *  
 *  Copyright (C) 2001-2020 Imixs Software Solutions GmbH,  
 *  http://www.imixs.com
 *  
 *  This program is free software; you can redistribute it and/or 
 *  modify it under the terms of the GNU General Public License 
 *  as published by the Free Software Foundation; either version 2 
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful, 
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of 
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 *  General Public License for more details.
 *  
 *  You can receive a copy of the GNU General Public
 *  License at http://www.gnu.org/licenses/gpl.html
 *  
 *  Project: 
 *      https://www.imixs.org
 *      https://github.com/imixs/imixs-workflow
 *  
 *  Contributors:  
 *      Imixs Software Solutions GmbH - Project Management
 *      Ralph Soika - Software Developer
 */

package org.imixs.workflow.engine.plugins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import javax.xml.transform.TransformerException;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.exceptions.PluginException;
import org.imixs.workflow.xml.XMLDocument;
import org.imixs.workflow.xml.XMLDocumentAdapter;
import org.imixs.workflow.xml.XSLHandler;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.ArrayList;

/**
 * This plug-in supports a Mail interface to send a email to a list of
 * recipients. The mail content can be defined by the corresponding BPMN event.
 * 
 * The content of the email can either be plain text or HTML.
 * 
 * The plug-in uses a JNID messaging session named 'org.imixs.workflow.mail'.
 * The JNDI resource can be customized by the deployment constructor.
 * 
 * The e-mail message can be canceled by the application or another plug-in by
 * setting the attribute keyMailInactive=true
 * 
 * @author Ralph Soika
 * 
 */
public class MailPlugin extends AbstractPlugin {

    public static final String ERROR_INVALID_XSL_FORMAT = "INVALID_XSL_FORMAT";
    public static final String ERROR_MAIL_MESSAGE = "ERROR_MAIL_MESSAGE";
    public static final String MAIL_SESSION_NAME = "mail/org.imixs.workflow.mail";
    public static final String CONTENTTYPE_TEXT_PLAIN = "text/plain";
    public static final String CONTENTTYPE_TEXT_HTML = "text/html";
    public static final String INVALID_ADDRESS = "INVALID_ADDRESS";

    // Mail objects
    @Resource(lookup = MAIL_SESSION_NAME)
    Session mailSession;

    @Inject
    @ConfigProperty(name = "mail.testRecipients")
    Optional<String> mailTestRecipients;

    @Inject
    @ConfigProperty(name = "mail.defaultSender")
    Optional<String> mailDefaultSender;

    @Inject
    @ConfigProperty(name = "mail.charSet", defaultValue = "ISO-8859-1")
    String mailCharSet;

    private MimeMessage mailMessage = null;
    private Multipart mimeMultipart = null;
    private String charSet = "ISO-8859-1";

    private boolean bHTMLMail = false;
    private static final Logger logger = Logger.getLogger(MailPlugin.class.getName());

    /**
     * The run method creates a mailMessage object if recipients are defined by the
     * corresponding BPMN event. The mail message will finally be send in the close
     * method. This mechanism avoids that a mail is send before all plug-ins were
     * processed correctly.
     * 
     * @param documentContext
     * @param documentActivity
     */
    @Override
    public ItemCollection run(ItemCollection documentContext, ItemCollection documentActivity) throws PluginException {
        boolean debug = logger.isLoggable(Level.FINE);
        mailMessage = null;

        // check if mail is active? This flag can be set by another plug-in
        if (documentActivity.getItemValueBoolean("keyMailInactive")
                || "1".equals(documentActivity.getItemValueString("keyMailInactive"))) {
            if (debug) {
                logger.finest("......keyMailInactive = true - cancel mail message.");
            }
            return documentContext;
        }

        List<String> recipients = getRecipients(documentContext, documentActivity);
        if (recipients.isEmpty()) {
            if (debug) {
                logger.finest("......No Receipients defined for this Activity - cancel mail message.");
            }
            return documentContext;
        }

        try {

            // first initialize mail message object
            initMailMessage();

            if (mailMessage == null) {
                logger.warning(" mailMessage = null");
                return documentContext;
            }

            // set FROM
            InternetAddress adr = getInternetAddress(getFrom(documentContext, documentActivity));
            if (adr==null) {
                // in case of null we set an emtpy address here. This can happen in case
                // of a RunAs Service EJB where the user context can not be resoved to a valid smtp address
                if (debug) {
                    logger.warning("...from address was resolved to null");
                }
                // will force a MessagingException...
                adr=new InternetAddress("");
            }
            mailMessage.setFrom(adr);

            // set Recipient
            mailMessage.setRecipients(Message.RecipientType.TO, getInternetAddressArray(recipients));

            // build CC
            mailMessage.setRecipients(Message.RecipientType.CC,
                    getInternetAddressArray(getRecipientsCC(documentContext, documentActivity)));

            // build BCC
            mailMessage.setRecipients(Message.RecipientType.BCC,
                    getInternetAddressArray(getRecipientsBCC(documentContext, documentActivity)));

            // replay to?
            String sReplyTo = getReplyTo(documentContext, documentActivity);
            if ((sReplyTo != null) && (!sReplyTo.isEmpty())) {
                InternetAddress[] resplysAdrs = new InternetAddress[1];
                resplysAdrs[0] = getInternetAddress(sReplyTo);
                mailMessage.setReplyTo(resplysAdrs);
            }

            // set Subject
            mailMessage.setSubject(getSubject(documentContext, documentActivity), this.getCharSet());

            // set Body
            String aBodyText = getBody(documentContext, documentActivity);

            // set mailbody
            MimeBodyPart messagePart = new MimeBodyPart();
            if (debug) {
                logger.log(Level.FINEST, "......ContentType: ''{0}''", getContentType());
            }
            messagePart.setContent(aBodyText, getContentType());
            // append message part
            mimeMultipart.addBodyPart(messagePart);
            // mimeMulitPart object can be extended from subclases

        } catch (MessagingException e) {
            throw new PluginException(MailPlugin.class.getSimpleName(), ERROR_MAIL_MESSAGE, e.getMessage(), e);
        }

        return documentContext;
    }

    /**
     * Send the mail if the object 'mailMessage' is not null.
     * 
     * The method lookups the mail session from the session
     * @param rollbackTransaction
     * @throws org.imixs.workflow.exceptions.PluginException
     */
    @Override
    public void close(boolean rollbackTransaction) throws PluginException {
        if (!rollbackTransaction && mailSession != null && mailMessage != null) {
            boolean debug = logger.isLoggable(Level.FINE);
            // Send the message
            try {

                // Check if we are running in a Test MODE

                // test if TestReceipiens are defined...
                if (mailTestRecipients.isPresent() && !mailTestRecipients.get().isEmpty()) {
                    List<String> vRecipients = new ArrayList<>();
                    // split multivalues
                    StringTokenizer st = new StringTokenizer(mailTestRecipients.get(), ",", false);
                    while (st.hasMoreElements()) {
                        vRecipients.add(st.nextToken().trim());
                    }

                    logger.info("Running in TestMode, forwarding mails to:");
                    for (String adr : vRecipients) {
                        logger.log(Level.INFO, "     {0}", adr);
                    }
                    try {
                        getMailMessage().setRecipients(Message.RecipientType.CC, null);
                        getMailMessage().setRecipients(Message.RecipientType.BCC, null);
                        getMailMessage().setRecipients(Message.RecipientType.TO, getInternetAddressArray(vRecipients));
                        // change subject
                        String sSubject = getMailMessage().getSubject();
                        getMailMessage().setSubject("[TESTMODE] : " + sSubject);

                    } catch (MessagingException e) {
                        throw new PluginException(MailPlugin.class.getSimpleName(), INVALID_ADDRESS,
                                " unable to set mail recipients: ", e);
                    }
                }
                if (debug) {
                    logger.finest("......sending message...");
                }
                mailMessage.setContent(mimeMultipart, getContentType());
                mailMessage.saveChanges();

                // Issue #452 - optional authentication
                // A simple transport.send command did not work if mail host needs a
                // authentification. Therefore we use a manual SMTP connection
                if (mailSession.getProperty("mail.smtp.password") != null
                        && !mailSession.getProperty("mail.smtp.password").isEmpty()) {
                    try ( // create transport object with authentication data
                            Transport trans = mailSession.getTransport("smtp")) {
                        trans.connect(mailSession.getProperty("mail.smtp.user"),
                                mailSession.getProperty("mail.smtp.password"));
                        trans.sendMessage(mailMessage, mailMessage.getAllRecipients());
                    }
                } else {
                    long l = System.currentTimeMillis();
                    try ( // no authentication - so we simple send the mail...
                    // Transport.send(mailMessage);
                    // issue #467
                            Transport trans = mailSession.getTransport("smtp")) {
                        trans.connect();
                        trans.sendMessage(mailMessage, mailMessage.getAllRecipients());
                    }
                    if (debug) {
                        logger.log(Level.FINEST, "...mail transfer in {0}ms", System.currentTimeMillis() - l);
                    }
                }
                logger.log(Level.INFO, "...send mail: MessageID={0}", mailMessage.getMessageID());

            } catch (MessagingException | PluginException esend) {
                logger.log(Level.WARNING, "close failed with exception: {0}", esend.toString());
            }
        }
    }

    /**
     * Computes the sender name. A sender can be defined by the event property
     * 'namMailFrom' or by the system property 'mail.defaultSender'. If no sender is
     * defined, the method takes the current username.
     * 
     * This method can be overwritten by subclasses.
     * 
     * @param documentContext
     * @param documentActivity
     * @return String - mail seder
     */
    public String getFrom(ItemCollection documentContext, ItemCollection documentActivity) {
        boolean debug = logger.isLoggable(Level.FINE);
        // test if namMailReplyToUser is defined by event
        String sFrom = documentActivity.getItemValueString("namMailFrom");

        // if no from was defined by teh event, we test if a default sender is defined
        if (sFrom.isEmpty() && mailDefaultSender.isPresent()) {
            sFrom = mailDefaultSender.get();
        }
        // if no default sender take the current username
        if (sFrom == null || sFrom.isEmpty())
            sFrom = this.getWorkflowService().getUserName();
        if (debug) {
            logger.log(Level.FINEST, "......From: {0}", sFrom);
        }
        return sFrom;
    }

    /**
     * Computes the replyTo address from current workflow activity. This method can
     * be overwritten by subclasses.
     * 
     * @param documentContext
     * @param documentActivity
     * @return String - replyTo address
     */
    public String getReplyTo(ItemCollection documentContext, ItemCollection documentActivity) {
        String sReplyTo;
        boolean debug = logger.isLoggable(Level.FINE);

        // check for ReplyTo...
        if ("1".equals(documentActivity.getItemValueString("keyMailReplyToCurrentUser")))
            sReplyTo = this.getWorkflowService().getUserName();
        else
            sReplyTo = documentActivity.getItemValueString("namMailReplyToUser");
        if (debug) {
            logger.log(Level.FINEST, "......ReplyTo={0}", sReplyTo);
        }
        return sReplyTo;
    }

    /**
     * Computes the mail subject from the current workflow activity. This method can
     * be overwritten by subclasses.
     * 
     * @param documentContext
     * @param documentActivity
     * @return String - mail subject
     * @throws PluginException
     */
    public String getSubject(ItemCollection documentContext, ItemCollection documentActivity) throws PluginException {
        boolean debug = logger.isLoggable(Level.FINE);
        String subject = getWorkflowService().adaptText(documentActivity.getItemValueString("txtMailSubject"),
                documentContext);
        if (debug) {
            logger.log(Level.FINEST, "......Subject: {0}", subject);
        }
        return subject;
    }

    /**
     * Computes the mail Recipients from the current workflow activity. This method
     * can be overwritten by subclasses.
     * 
     * @param documentContext
     * @param documentActivity
     * @return String list of Recipients
     */
    public List<String> getRecipients(ItemCollection documentContext, ItemCollection documentActivity) {

        // build Recipient from Activity ...
        List<String> recipients = documentActivity.getItemValueList("namMailReceiver", String.class);
        if (recipients == null)
            recipients = new ArrayList<>();

        // read keyMailReceiverFields (multi value)
        // here are the field names defined
        mergeFieldList(documentContext, recipients, documentActivity.getItemValueList("keyMailReceiverFields", String.class));

        // write debug Log
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINEST, "......{0} Receipients: ", recipients.size());
            for (String rez : recipients)
                logger.log(Level.FINEST, "     {0}", rez);
        }

        return recipients;
    }

    /**
     * Computes the mail RecipientsCC from the current workflow activity. This
     * method can be overwritten by subclasses.
     * 
     * @param documentContext
     * @param documentActivity
     * @return String list of Recipients
     */
    public List<String> getRecipientsCC(ItemCollection documentContext, ItemCollection documentActivity) {

        // build Recipient List from namMailReceiverCC
        List<String> recipients = documentActivity.getItemValueList("namMailReceiverCC", String.class);
        if (recipients == null)
            recipients = new ArrayList<>();

        // now read keyMailReceiverFieldsCC (multiValue)
        mergeFieldList(documentContext, recipients, documentActivity.getItemValueList("keyMailReceiverFieldsCC", String.class));

        // write debug Log
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINEST, "......{0} ReceipientsCC: ", recipients.size());
            for (String rez : recipients)
                logger.log(Level.FINEST, "     {0}", rez);
        }
        return recipients;
    }

    /**
     * Computes the mail RecipientsBCC from the current workflow activity. This
     * method can be overwritten by subclasses.
     * 
     * @param documentContext
     * @param documentActivity
     * @return String list of Recipients
     */
    public List<String> getRecipientsBCC(ItemCollection documentContext, ItemCollection documentActivity) {

        // build Recipient List from namMailReceiverBCC
        List<String> recipients = documentActivity.getItemValueList("namMailReceiverBCC", String.class);
        if (recipients == null)
            recipients = new ArrayList<>();

        // now read keyMailReceiverFieldsCC (multiValue)
        mergeFieldList(documentContext, recipients, documentActivity.getItemValueList("keyMailReceiverFieldsBCC", String.class));

        // write debug Log
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINEST, "......{0} ReceipientsBCC: ", recipients.size());
            for (String rez : recipients)
                logger.log(Level.FINEST, "     {0}", rez);
        }
        return recipients;
    }

    /**
     * Computes the mail body from the current workflow event. The method also
     * updates the internal flag HTMLMail to indicate if the mail is send as HTML
     * mail.
     * 
     * In case the content contains a XSL Template, the template will be processed
     * with the current document structure.
     * 
     * The method can be overwritten by subclasses.
     * 
     * @param documentContext
     * @param documentActivity
     * @return String - mail subject
     * @throws PluginException
     */
    public String getBody(ItemCollection documentContext, ItemCollection documentActivity) throws PluginException {
        // build mail body and replace dynamic values...
        String aBodyText = getWorkflowService().adaptText(documentActivity.getItemValueString("rtfMailBody"),
                documentContext);

        // Test if mail body contains HTML content and updates the flag
        // 'isHTMLMail'.
        String sTestHTML = aBodyText.trim().toLowerCase();
        bHTMLMail = sTestHTML.startsWith("<!doctype") || sTestHTML.startsWith("<html") || sTestHTML.startsWith("<?xml");

        // xsl transformation...?
        if (sTestHTML.contains("<xsl:stylesheet")) {
            aBodyText = transformXSLBody(documentContext, aBodyText);
        }

        return aBodyText;

    }

    /**
     * This method performs a XSL transformation based on the current Mail Body
     * text. The xml source is generated form the current document context.
     * 
     * encoding is set to UTF-8
     * 
     * @param documentContext
     * @param xslTemplate
     * @return translated email body
     * @throws PluginException
     * 
     */
    public String transformXSLBody(ItemCollection documentContext, String xslTemplate) throws PluginException {
        String encoding;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        encoding = "UTF-8";
        boolean debug = logger.isLoggable(Level.FINE);

        if (debug) {
            logger.finest("......transfor mail body based on XSL template....");
        }
        // Transform XML per XSL and generate output
        XMLDocument xml;
        try {
            xml = XMLDocumentAdapter.getDocument(documentContext);
            StringWriter writer = new StringWriter();

            JAXBContext context = JAXBContext.newInstance(XMLDocument.class);
            Marshaller m = context.createMarshaller();
            m.setProperty("jaxb.encoding", encoding);
            m.marshal(xml, writer);

            // create a ByteArray Output Stream
            XSLHandler.transform(writer.toString(), xslTemplate, encoding, outputStream);
            return outputStream.toString(encoding);

        } catch (JAXBException | UnsupportedEncodingException | TransformerException e) {
            logger.warning("Error processing XSL template!");
            throw new PluginException(MailPlugin.class.getSimpleName(), ERROR_INVALID_XSL_FORMAT, e.getMessage(), e);
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                logger.severe(e.getMessage());
            }
        }

    }

    /**
     * initializes a new mail Message object
     * 
     * @throws AddressException
     * @throws MessagingException
     */
    public void initMailMessage() throws AddressException, MessagingException {
        boolean debug = logger.isLoggable(Level.FINE);
        if (debug) {
            logger.finest("......initializeMailMessage...");
        }
        if (mailSession == null) {
            logger.warning(" Lookup MailSession '" + MAIL_SESSION_NAME + "' failed: ");
            logger.warning(" Unable to send mails! Verify server resources -> mail session.");
        } else {

            // test for property mail.charSet
            if (mailCharSet != null && !mailCharSet.isEmpty()) {
                setCharSet(mailCharSet);

            }

            // log mail Properties ....
            if (debug) {
                Properties props = mailSession.getProperties();
                Enumeration<Object> enumer = props.keys();
                while (enumer.hasMoreElements()) {
                    String aKey = enumer.nextElement().toString();
                    logger.log(Level.FINEST, "...... ProperyName= {0}", aKey);
                    Object value = props.getProperty(aKey);
                    if (value == null)
                        logger.finest("...... PropertyValue=null");
                    else
                        logger.log(Level.FINEST, "...... PropertyValue= {0}", props.getProperty(aKey));
                }
            }
            mailMessage = new MimeMessage(mailSession);
            mailMessage.setSentDate(new Date());
            mailMessage.setFrom();
            mimeMultipart = new MimeMultipart();
        }
    }

    /**
     * This method creates an InternetAddress from a string. If the string has
     * illegal characters like whitespace the string will be surrounded with "".
     * 
     * The method can be overwritten by subclasses to return a different
     * mail-address name or lookup a mail attribute in a directory.
     * 
     * @param aAddr string
     * @return InternetAddress
     * @throws AddressException
     */
    public InternetAddress getInternetAddress(String aAddr) throws AddressException {
        InternetAddress inetAddr;
        if (aAddr == null) {
            return null;
        }
        aAddr=aAddr.trim();
        try {
            // surround with "" if space
            if (aAddr.contains(" "))
                inetAddr = new InternetAddress("\"" + aAddr + "\"");
            else
                inetAddr = new InternetAddress(aAddr);

        } catch (AddressException ae) {
            // return empty address part
            logger.severe(ae.getMessage());
            return null;
        }
        return inetAddr;
    }

    /**
     * This method transforms a List of E-Mail addresses into an InternetAddress
     * Array. Null values will be removed from list
     * 
     * @param String List of adresses
     * @return array of InternetAddresses
     */
    private InternetAddress[] getInternetAddressArray(List<String> aList) {
        // set TO Recipient
        // store valid addresses into atemp list to avoid null values
        InternetAddress inetAddr;
        if (aList == null) {
            return null;
        }

        List<InternetAddress> receipsTemp = new ArrayList<>();
        for (int i = 0; i < aList.size(); i++) {
            try {
                inetAddr = getInternetAddress(aList.get(i));
                if (inetAddr != null && !"".equals(inetAddr.getAddress()))
                    receipsTemp.add(inetAddr);
            } catch (AddressException e) {
                // no todo
            }

        }

        // rebuild new InternetAddress array from TempList...
        InternetAddress[] receipsAdrs = new InternetAddress[receipsTemp.size()];
        for (int i = 0; i < receipsTemp.size(); i++) {
            receipsAdrs[i] = receipsTemp.get(i);
        }
        return receipsAdrs;
    }

    /**
     * This method returns the mail session object.
     * @return the mail session object
     */
    public Session getMailSession() {
        return mailSession;
    }

    public Message getMailMessage() {
        return mailMessage;
    }

    public Multipart getMultipart() {
        return mimeMultipart;
    }

    /**
     * Return true if the mail body contains HTML content.
     * 
     * @return
     */
    public boolean isHTMLMail() {
        return bHTMLMail;
    }

    public String getCharSet() {
        return charSet;
    }

    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }

    /**
     * This method returns a string representing the mail content type. The content
     * type depends on the content of the mail body (html or plaintext) and contains
     * optional the character set.
     * 
     * If the mail is a HTML mail then the returned string contains 'text/html'
     * otherwise it will contain 'text/plain'.
     * 
     * The content
     * 
     * @return
     */
    public String getContentType() {
        StringBuilder sContentType = new StringBuilder();
        if (bHTMLMail) {
            sContentType.append(CONTENTTYPE_TEXT_HTML);
        } else {
            sContentType.append(CONTENTTYPE_TEXT_PLAIN);
        }
        if (getCharSet() != null && !getCharSet().isEmpty()) {
            sContentType.append("; charset=")
            .append(getCharSet());
        }
        return sContentType.toString();
    }

}
