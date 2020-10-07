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

package org.imixs.workflow.engine.scheduler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.annotation.security.RunAs;
import javax.ejb.LocalBean;
import javax.ejb.ScheduleExpression;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import org.imixs.workflow.ItemCollection;
import org.imixs.workflow.engine.DocumentService;
import org.imixs.workflow.exceptions.AccessDeniedException;
import org.imixs.workflow.exceptions.InvalidAccessException;
import org.imixs.workflow.exceptions.QueryException;

/**
 * The SchedulerService EJB can be used to start, monitor and stop custom
 * scheduler implementation. A Scheduler Implementation must implement the
 * Interface "org.imixs.workflow.engine.scheduler.Scheduler".
 * <p>
 * A scheduler definition is stored in a document with the type "scheduler". The
 * document can provide concrete information to process the timer event.
 * <p>
 * The TimerService can be started using the method start(). The Methods
 * findTimerDescription and findAllTimerDescriptions are used to lookup enabled
 * and running service instances.
 * <p>
 * Each Method expects or generates a TimerDescription Object. This object is an
 * instance of a ItemCollection. To create a new timer the ItemCollection should
 * contain the following attributes:
 * <p>
 * <ul>
 * <li>type - fixed to value 'scheduler'</li>
 * <li>_scheduler_definition - the chron/calendar definition for the Java EE
 * timer service.</li>
 * <li>_scheduler_enabled - boolean indicates if the scheduler is
 * enabled/disabled</li>
 * <li>_scheduler_class - class name of the scheduler implementation</li>
 * <li>_scheduelr_log - optional log information
 * </ul>
 * <p>
 * the following additional attributes are generated by the finder methods and
 * can be used by an application to verfiy the status of a running instance:
 * <ul>
 * <li>nextTimeout - Next Timeout - pint of time when the service will be
 * scheduled</li>
 * <li>timeRemaining - Timeout in milliseconds</li>
 * <li>statusmessage - text message</li>
 * </ul>
 * 
 * @author rsoika
 * @version 1.0
 */
@Stateless
@LocalBean
@DeclareRoles({ "org.imixs.ACCESSLEVEL.MANAGERACCESS" })
@RunAs("org.imixs.ACCESSLEVEL.MANAGERACCESS")
public class SchedulerService {

    public static final String DOCUMENT_TYPE = "scheduler";

    @Resource
    SessionContext ctx;

    @Inject
    DocumentService documentService;

    @Resource
    javax.ejb.TimerService timerService;

    @Inject
    SchedulerConfigurationService schedulerSaveService;

    @Inject
    @Any
    private Instance<Scheduler> schedulerHandlers;

    private static Logger logger = Logger.getLogger(SchedulerService.class.getName());

    /**
     * Loads the scheduler configuration entity by name. The method returns null if
     * no scheduler configuration exits.
     * 
     * @return
     */
    public ItemCollection loadConfiguration(String name) {
        try {
            // support deprecated txtname attribure
            String sQuery = "(type:\"" + DOCUMENT_TYPE + "\" AND (name:\"" + name + "\" OR txtname:\"" + name
                    + "\" ) )";
            Collection<ItemCollection> col = documentService.find(sQuery, 1, 0);
            // check if we found a scheduler configuration
            if (col.size() > 0) {
                ItemCollection configuration = col.iterator().next();
                // refresh timer details
                updateTimerDetails(configuration);
                return configuration;
            }
        } catch (QueryException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    /**
     * This method saves the scheduler configuration. The method ensures that the
     * following properties are set to default.
     * <ul>
     * <li>type</li>
     * <li>name</li>
     * <li>$writeAccess</li>
     * <li>$readAccess</li>
     * </ul>
     * The method also updates the timer details of a running timer.
     * 
     * @return
     * @throws AccessDeniedException
     */
    public ItemCollection saveConfiguration(ItemCollection configItemCollection) {

        // validate and migrate deprecated 'txtname' field
        String name = configItemCollection.getItemValueString("name");
        if (name.isEmpty()) {
            name = configItemCollection.getItemValueString("txtname");
            configItemCollection.replaceItemValue("name", name);
        }
        if (name == null || name.isEmpty()) {
            throw new InvalidAccessException(SchedulerService.class.getName(), SchedulerException.INVALID_WORKITEM,
                    " scheduler configuraiton must contain the item 'name'");
        }

        // update write and read access
        configItemCollection.replaceItemValue("type", DOCUMENT_TYPE);
        configItemCollection.replaceItemValue("$snapshot.history", 1);
        configItemCollection.replaceItemValue("$writeAccess", "org.imixs.ACCESSLEVEL.MANAGERACCESS");
        configItemCollection.replaceItemValue("$readAccess", "org.imixs.ACCESSLEVEL.MANAGERACCESS");

        // refesh timer details
        updateTimerDetails(configItemCollection);
        // save entity in new transaction
        configItemCollection = documentService.save(configItemCollection);

        return configItemCollection;
    }

    /**
     * Starts a new Timer for the scheduler defined by the Configuration.
     * <p>
     * The Timer can be started based on a Calendar setting stored in the property
     * _scheduler_definition.
     * <p>
     * The $UniqueID of the configuration entity is the id of the timer to be
     * controlled.
     * <p>
     * The method throws an exception if the configuration entity contains invalid
     * attributes or values.
     * <p>
     * After the timer was started the configuration is updated with the latest
     * statusmessage. The item _schedueler_enabled will be set to 'true'.
     * <p>
     * The method returns the updated configuration. The configuration will not be
     * saved!
     * 
     * @param configuration - scheduler configuration
     * @return updated configuration
     * @throws AccessDeniedException
     * @throws ParseException
     */
    public ItemCollection start(ItemCollection configuration) throws AccessDeniedException, ParseException {
        Timer timer = null;
        if (configuration == null)
            return null;

        String id = configuration.getUniqueID();
        // try to cancel an existing timer for this workflowinstance
        timer = findTimer(id);
        if (timer != null) {
            try {
                timer.cancel();
                timer = null;
            } catch (Exception e) {
                logger.warning("...failed to stop existing timer for '" + configuration.getUniqueID() + "'!");
                throw new InvalidAccessException(SchedulerService.class.getName(), SchedulerException.INVALID_WORKITEM,
                        " failed to cancle existing timer!");
            }
        }

        logger.info("...Scheduler Service " + configuration.getUniqueID() + " will be started...");
        String schedulerDescription = configuration.getItemValueString(Scheduler.ITEM_SCHEDULER_DEFINITION);

        if (!schedulerDescription.isEmpty()) {
            // New timer will be started on calendar confiugration
            timer = createTimerOnCalendar(configuration);
        }
        // start and set statusmessage
        if (timer != null) {

            Calendar calNow = Calendar.getInstance();
            SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");
            String msg = "started at " + dateFormatDE.format(calNow.getTime()) + " by "
                    + ctx.getCallerPrincipal().getName();
            configuration.replaceItemValue(Scheduler.ITEM_SCHEDULER_STATUS, msg);
            logger.info("...Scheduler Service " + id + " (" + configuration.getItemValueString("Name")
                    + ") successfull started.");
        }
        configuration.replaceItemValue(Scheduler.ITEM_SCHEDULER_ENABLED, true);
        // clear logs...
        configuration.replaceItemValue(Scheduler.ITEM_ERRORMESSAGE, "");
        configuration.replaceItemValue(Scheduler.ITEM_LOGMESSAGE, "");

        return configuration;
    }

    /**
     * Cancels a running timer instance. After cancel a timer the corresponding
     * timerDescripton (ItemCollection) is no longer valid.
     * <p>
     * The method returns the current configuration. The configuration will not be
     * saved!
     * 
     * 
     */
    public ItemCollection stop(ItemCollection configuration) {
        Timer timer = findTimer(configuration.getUniqueID());
        return stop(configuration, timer);

    }

    public ItemCollection stop(ItemCollection configuration, Timer timer) {
        if (timer != null) {
            try {
                timer.cancel();
            } catch (Exception e) {
                logger.info("...failed to stop timer for '" + configuration.getUniqueID() + "'!");
            }

            // update status message
            Calendar calNow = Calendar.getInstance();
            SimpleDateFormat dateFormatDE = new SimpleDateFormat("dd.MM.yy hh:mm:ss");

            String message = "stopped at " + dateFormatDE.format(calNow.getTime());
            String name = ctx.getCallerPrincipal().getName();
            if (name != null && !name.isEmpty() && !"anonymous".equals(name)) {
                message += " by " + name;
            }
            configuration.replaceItemValue(Scheduler.ITEM_SCHEDULER_STATUS, message);

            logger.info("... scheduler " + configuration.getItemValueString("Name") + " stopped: "
                    + configuration.getUniqueID());
        } else {
            String msg = "stopped";
            configuration.replaceItemValue(Scheduler.ITEM_SCHEDULER_STATUS, msg);

        }
        configuration.removeItem("nextTimeout");
        configuration.removeItem("timeRemaining");
        configuration.replaceItemValue(Scheduler.ITEM_SCHEDULER_ENABLED, false);
        Calendar cal = Calendar.getInstance();
        configuration.appendItemValue(Scheduler.ITEM_LOGMESSAGE, "Stopped: " + cal.getTime());
        return configuration;
    }

    /**
     * This method will start all schedulers which are not yet started. The method
     * is called for example by the SchedulerStartupServlet.
     * 
     */
    public void startAllSchedulers() {
        logger.info("...starting Imixs Schedulers....");
        try {
            String sQuery = "(type:\"" + SchedulerService.DOCUMENT_TYPE + "\" )";
            Collection<ItemCollection> col = documentService.find(sQuery, 101, 0);
            if (col.size() > 100) {
                // Issue #568 - we do not support more than 100 jobs in parallel!
                logger.severe(
                        "More than 100 waiting scheduler jobs found but a maximum of 100 jobs will be started in parallel. Please report this issue to the imixs-workflow project!");
            }
            // check if we found a scheduler configuration
            for (ItemCollection schedulerConfig : col) {
                // is timmer running?
                if (schedulerConfig != null && schedulerConfig.getItemValueBoolean(Scheduler.ITEM_SCHEDULER_ENABLED)) {
                    try {

                        if (findTimer(schedulerConfig.getUniqueID()) == null) {
                            start(schedulerConfig);
                        } else {
                            logger.info("...Scheduler Service " + schedulerConfig.getUniqueID() + " already running. ");
                        }
                    } catch (Exception e) {
                        logger.severe("...start of Scheduler Service " + schedulerConfig.getUniqueID() + " failed! - "
                                + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    logger.info("...Scheduler Service " + schedulerConfig.getUniqueID() + " is not enabled. ");
                }
            }
        } catch (QueryException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * This method returns a timer for a corresponding id if such a timer object
     * exists.
     * 
     * @param id
     * @return Timer
     * @throws Exception
     */
    public Timer findTimer(String id) {
        for (Object obj : timerService.getTimers()) {
            Timer timer = (javax.ejb.Timer) obj;
            if (id.equals(timer.getInfo())) {
                return timer;
            }
        }
        return null;
    }

    /**
     * Updates the timer details of a running timer service. The method updates the
     * properties netxtTimeout and timeRemaining and store them into the timer
     * configuration.
     * 
     * @param configuration - the current scheduler configuration to be updated.
     */
    public void updateTimerDetails(ItemCollection configuration) {
        if (configuration == null)
            return;// configuration;
        String id = configuration.getUniqueID();
        Timer timer;
        try {
            timer = this.findTimer(id);
            if (timer != null) {
                // load current timer details
                configuration.replaceItemValue("nextTimeout", timer.getNextTimeout());
                configuration.replaceItemValue("timeRemaining", timer.getTimeRemaining());
            } else {
                configuration.removeItem("nextTimeout");
                configuration.removeItem("timeRemaining");
            }
        } catch (Exception e) {
            logger.warning("unable to updateTimerDetails: " + e.getMessage());
            configuration.removeItem("nextTimeout");
            configuration.removeItem("timeRemaining");
        }
    }

    /**
     * Creates a new log entry stored in the item _scheduler_log. The log can be
     * writen optional to the scheduler configuration and a workitem.
     * 
     * @param message
     * @param configuration
     */
    public void logMessage(String message, ItemCollection configuration, ItemCollection workitem) {
        if (configuration != null) {
            configuration.appendItemValue(Scheduler.ITEM_LOGMESSAGE, message);
        }
        if (workitem != null) {
            workitem.appendItemValue(Scheduler.ITEM_LOGMESSAGE, message);
        }

        logger.info(message);

    }

    /**
     * Creates a new log entry stored in the item _scheduler_log. The log can be
     * writen optional to the scheduler configuration and a workitem.
     * 
     * @param message
     * @param configuration
     */
    public void logWarning(String message, ItemCollection configuration, ItemCollection workitem) {
        if (configuration != null) {
            configuration.appendItemValue(Scheduler.ITEM_LOGMESSAGE, message);
        }
        if (workitem != null) {
            workitem.appendItemValue(Scheduler.ITEM_LOGMESSAGE, message);
        }

        logger.warning(message);

    }

    /**
     * This method returns a n injected JobHandler by name or null if no JobHandler
     * with the requested class name is injected.
     * 
     * @param jobHandlerClassName
     * @return jobHandler class or null if not found
     */
    protected Scheduler findSchedulerByName(String schedulerClassName) {
        if (schedulerClassName == null || schedulerClassName.isEmpty()) {
            return null;
        }
        boolean debug = logger.isLoggable(Level.FINE);

        if (schedulerHandlers == null || !schedulerHandlers.iterator().hasNext()) {
            if (debug) {
                logger.finest("......no CDI schedulers injected");
            }
            return null;
        }

        logger.finest("......injecting CDI Scheduler '" + schedulerClassName + "'...");
        // iterate over all injected JobHandlers....
        for (Scheduler scheduler : this.schedulerHandlers) {
            if (scheduler.getClass().getName().equals(schedulerClassName)) {
                if (debug) {
                    logger.finest("......CDI Scheduler class '" + schedulerClassName + "' successful injected");
                }
                return scheduler;
            }
        }

        return null;
    }

    /**
     * This is the method which processes the timeout event depending on the running
     * timer settings. The method calls the abstract method 'process' which need to
     * be implemented by a subclass.
     * 
     * @param timer
     * @throws Exception
     * @throws QueryException
     */
    @Timeout
    protected void onTimeout(javax.ejb.Timer timer) {
        String errorMes = "";
        // start time....
        long lProfiler = System.currentTimeMillis();
        String id = timer.getInfo().toString();
        ItemCollection configuration = documentService.load(id);

        if (configuration == null) {
            logger.severe("...failed to load scheduler configuration for current timer. Timer will be stopped...");
            timer.cancel();
            return;
        }

        try {
            // ...start processing
            String schedulerClassName = configuration.getItemValueString(Scheduler.ITEM_SCHEDULER_CLASS);

            Scheduler scheduler = findSchedulerByName(schedulerClassName);
            if (scheduler != null) {
                logger.info("...run scheduler '" + id + "' scheduler class='" + schedulerClassName + "'....");
                Calendar cal = Calendar.getInstance();
                configuration.replaceItemValue(Scheduler.ITEM_LOGMESSAGE, "Started: " + cal.getTime());

                configuration = scheduler.run(configuration);
                logger.info("...run scheduler  '" + id + "' finished in: " + ((System.currentTimeMillis()) - lProfiler)
                        + " ms");
                cal = Calendar.getInstance();
                configuration.appendItemValue(Scheduler.ITEM_LOGMESSAGE, "Finished: " + cal.getTime());
                if (configuration.getItemValueBoolean(Scheduler.ITEM_SCHEDULER_ENABLED) == false) {
                    logger.info("...scheduler '" + id + "' disabled -> timer will be stopped...");
                    stop(configuration);
                }
            } else {
                errorMes = "Scheduler class='" + schedulerClassName + "' not found!";
                logger.warning("...scheduler '" + id + "' scheduler class='" + schedulerClassName
                        + "' not found, timer will be stopped...");
                configuration.setItemValue(Scheduler.ITEM_SCHEDULER_ENABLED, false);

                stop(configuration);
            }
        } catch (SchedulerException e) {
            // in case of an SchedulerException we cancel the Timer service
            if (logger.isLoggable(Level.FINEST)) {
                e.printStackTrace();
            }
            errorMes = e.getMessage();
            logger.severe("Scheduler '" + id + "' failed: " + errorMes);
            configuration.appendItemValue(Scheduler.ITEM_LOGMESSAGE, "Error: " + errorMes);
            configuration = stop(configuration, timer);
            
        } catch (RuntimeException e) {
            // in case of an RuntimeException we did not cancel the Timer service
            e.printStackTrace();
            errorMes = e.getMessage();
            logger.severe("Scheduler '" + id + "' failed: " + errorMes);
            configuration.appendItemValue(Scheduler.ITEM_LOGMESSAGE, "Error: " + errorMes);
            configuration = stop(configuration, timer);
            
        } finally {
            // Save statistic in configuration
            if (configuration != null) {
                configuration.replaceItemValue(Scheduler.ITEM_ERRORMESSAGE, errorMes);
                schedulerSaveService.storeConfigurationInNewTransaction(configuration);

            }
        }
    }

    /**
     * Create a calendar-based timer based on a input schedule expression. The
     * expression will be parsed by this method.
     * 
     * Example: <code>
     *   second=0
     *   minute=0
     *   hour=*
     *   dayOfWeek=
     *   dayOfMonth=
     *   month=
     *   year=*
     * </code>
     * 
     * @param sConfiguation
     * @return
     * @throws ParseException
     */
    protected Timer createTimerOnCalendar(ItemCollection configItemCollection) throws ParseException {

        TimerConfig timerConfig = new TimerConfig();
        timerConfig.setInfo(configItemCollection.getUniqueID());

        ScheduleExpression scheduerExpression = new ScheduleExpression();

        @SuppressWarnings("unchecked")
        List<String> calendarConfiguation = configItemCollection.getItemValue(Scheduler.ITEM_SCHEDULER_DEFINITION);
        // try to parse the configuration list....
        for (String confgEntry : calendarConfiguation) {

            if (confgEntry.startsWith("second=")) {
                scheduerExpression.second(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }
            if (confgEntry.startsWith("minute=")) {
                scheduerExpression.minute(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }
            if (confgEntry.startsWith("hour=")) {
                scheduerExpression.hour(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }
            if (confgEntry.startsWith("dayOfWeek=")) {
                scheduerExpression.dayOfWeek(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }
            if (confgEntry.startsWith("dayOfMonth=")) {
                scheduerExpression.dayOfMonth(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }
            if (confgEntry.startsWith("month=")) {
                scheduerExpression.month(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }
            if (confgEntry.startsWith("year=")) {
                scheduerExpression.year(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }
            if (confgEntry.startsWith("timezone=")) {
                scheduerExpression.timezone(confgEntry.substring(confgEntry.indexOf('=') + 1));
            }

            /* Start date */
            if (confgEntry.startsWith("start=")) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                Date convertedDate = dateFormat.parse(confgEntry.substring(confgEntry.indexOf('=') + 1));
                scheduerExpression.start(convertedDate);
            }

            /* End date */
            if (confgEntry.startsWith("end=")) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                Date convertedDate = dateFormat.parse(confgEntry.substring(confgEntry.indexOf('=') + 1));
                scheduerExpression.end(convertedDate);
            }

        }

        Timer timer = timerService.createCalendarTimer(scheduerExpression, timerConfig);

        return timer;

    }
}
