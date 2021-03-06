package org.coody.framework.box.task;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.coody.framework.box.annotation.CronTask;
import org.coody.framework.box.point.AspectPoint;
import org.coody.framework.util.DateUtils;
import org.coody.framework.util.StringUtil;

@Slf4j
public class TaskTrigger {


	
	public static Method getTriggerMethod(){
		Method[] methods=TaskTrigger.class.getDeclaredMethods();
		if(StringUtil.isNullOrEmpty(methods)){
			return null;
		}
		for(Method method:methods){
			if(method.getName().equals("taskTrigger")){			//获取到定时任务管理方法
				return method;
			}
		}
		return null;
	}

	private static Map<Method, ZonedDateTime> cronExpressionMap=new ConcurrentHashMap<Method, ZonedDateTime>();
	
	//作用：第一次调用---将定时任务放置在cronExpressionMap.put(method,zoneDateTime);
	public static void nextRun(Object bean,Method method,String cron,ZonedDateTime zonedDateTime){
		log.info("定时任务----nextRun");
		//获取下次执行时间
		CronExpression express = new CronExpression(cron);
		if(null==zonedDateTime){
			zonedDateTime=ZonedDateTime.now(ZoneId.systemDefault());
		}
		zonedDateTime=express.nextTimeAfter(zonedDateTime);
		cronExpressionMap.put(method, zonedDateTime);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DateUtils.DATETIME_PATTERN, Locale.CHINA);
		Date nextRunDate=DateUtils.toDate(zonedDateTime.toLocalDateTime().format(formatter));
		log.debug(bean.getClass().getName()+":"+method.getName()+" will run on "+DateUtils.toString(nextRunDate));
		long timeRage=nextRunDate.getTime()-new Date().getTime();
		TaskThreadPool.taskPool.schedule(new Runnable() {
			@Override
			public void run() {
				Object[] params={};
				try {
					log.info("线程池定时任务反射启动....");
					method.invoke(bean, params);
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		},timeRage , TimeUnit.MILLISECONDS);
	}
	
	/**
	 * 定时任务管理
	 * 
	 * @param aspect
	 * @return
	 * @throws Throwable
	 */
	public static Object taskTrigger(AspectPoint aspect) throws Throwable {
		log.info("定时任务管理-------");
		Method method=aspect.getMethod();
		CronTask cronTask=method.getAnnotation(CronTask.class);
		Object bean=aspect.getBean();
		String cron=cronTask.value();
		try{
			//切面进行反射
			log.info("-----taskTrigger---切面进行反射");
			return aspect.invoke();
		}finally {
			//反射完成后往线程池丢一个新的预计任务
			log.info("计算下次开动时间");
			ZonedDateTime zonedDateTime=cronExpressionMap.get(method);
			nextRun(bean, method, cron,zonedDateTime);
		}
	}
}
