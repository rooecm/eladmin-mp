/*
 *  Copyright 2019-2025 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package fun.rooe.admin.modules.quartz.utils;

import fun.rooe.admin.framework.utils.SpringBeanHolder;
import fun.rooe.admin.infrastructure.domain.dto.EmailDTO;
import fun.rooe.admin.infrastructure.service.EmailService;
import fun.rooe.admin.infrastructure.utils.RedisUtils;
import fun.rooe.admin.modules.quartz.domain.QuartzJobDO;
import fun.rooe.admin.modules.quartz.domain.QuartzLogDO;
import fun.rooe.admin.modules.quartz.mapper.QuartzLogMapper;
import fun.rooe.admin.modules.quartz.service.QuartzJobService;
import fun.rooe.admin.modules.system.utils.TemplateUtils;
import fun.rooe.admin.utils.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * 参考人人开源，<a href="https://gitee.com/renrenio/renren-security">...</a>
 *
 * @author /
 * @date 2019-01-07
 */
public class ExecutionJob extends QuartzJobBean {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    // 此处仅供参考，可根据任务执行情况自定义线程池参数
    private final ThreadPoolTaskExecutor executor = SpringBeanHolder.getBean("taskAsync");

    @Override
    public void executeInternal(JobExecutionContext context) {
        // 获取任务
        QuartzJobDO quartzJob = (QuartzJobDO) context.getMergedJobDataMap().get(QuartzJobDO.JOB_KEY);
        // 获取spring bean
        QuartzLogMapper quartzLogMapper = SpringBeanHolder.getBean(QuartzLogMapper.class);
        QuartzJobService quartzJobService = SpringBeanHolder.getBean(QuartzJobService.class);
        RedisUtils redisUtils = SpringBeanHolder.getBean(RedisUtils.class);

        String uuid = quartzJob.getUuid();

        QuartzLogDO log = new QuartzLogDO();
        log.setJobName(quartzJob.getJobName());
        log.setBeanName(quartzJob.getBeanName());
        log.setMethodName(quartzJob.getMethodName());
        log.setParams(quartzJob.getParams());
        long startTime = System.currentTimeMillis();
        log.setCronExpression(quartzJob.getCronExpression());
        try {
            // 执行任务
            QuartzRunnable task = new QuartzRunnable(quartzJob.getBeanName(), quartzJob.getMethodName(), quartzJob.getParams());
            Future<?> future = executor.submit(task);
            future.get();
            long times = System.currentTimeMillis() - startTime;
            log.setTime(times);
            if (StringUtils.isNotBlank(uuid)) {
                redisUtils.set(uuid, true);
            }
            // 任务状态
            log.setIsSuccess(true);
            logger.info("任务执行成功，任务名称：{}, 执行时间：{}毫秒", quartzJob.getJobName(), times);
            // 判断是否存在子任务
            if (StringUtils.isNotBlank(quartzJob.getSubTask())) {
                String[] tasks = quartzJob.getSubTask().split("[,，]");
                // 执行子任务
                quartzJobService.executionSubJob(tasks);
            }
        } catch (Exception e) {
            if (StringUtils.isNotBlank(uuid)) {
                redisUtils.set(uuid, false);
            }
            logger.error("任务执行失败，任务名称：{}", quartzJob.getJobName());
            long times = System.currentTimeMillis() - startTime;
            log.setTime(times);
            // 任务状态 0：成功 1：失败
            log.setIsSuccess(false);
            log.setExceptionDetail(ExceptionUtils.getStackTrace(e));
            // 任务如果失败了则暂停
            if (quartzJob.getPauseAfterFailure() != null && quartzJob.getPauseAfterFailure()) {
                quartzJob.setIsPause(false);
                //更新状态
                quartzJobService.updateIsPause(quartzJob);
            }
            if (quartzJob.getEmail() != null) {
                EmailService emailService = SpringBeanHolder.getBean(EmailService.class);
                // 邮箱报警
                if (StringUtils.isNoneBlank(quartzJob.getEmail())) {
                    EmailDTO emailDto = taskAlarm(quartzJob, ExceptionUtils.getStackTrace(e));
                    emailService.send(emailDto, emailService.find());
                }
            }
        } finally {
            quartzLogMapper.insert(log);
        }
    }

    private EmailDTO taskAlarm(QuartzJobDO quartzJob, String msg) {
        EmailDTO emailDto = new EmailDTO();
        emailDto.setSubject("定时任务【" + quartzJob.getJobName() + "】执行失败，请尽快处理！");
        Map<String, Object> data = new HashMap<>(16);
        data.put("task", quartzJob);
        data.put("msg", msg);
        emailDto.setContent(TemplateUtils.render("taskAlarm.ftl", data));
        List<String> emails = Arrays.asList(quartzJob.getEmail().split("[,，]"));
        emailDto.setTos(emails);
        return emailDto;
    }
}
