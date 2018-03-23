package org.coody.web.task;

import lombok.extern.slf4j.Slf4j;
import org.coody.framework.box.annotation.CronTask;
import org.coody.framework.box.annotation.InitBean;
import org.coody.framework.util.DateUtils;

@InitBean
@Slf4j
public class TestTask	 {

	@CronTask("0/5 * * * * ? ")
	public void test() {
		log.info("定时任务执行中:" + DateUtils.getDateTimeString());
	}

}
