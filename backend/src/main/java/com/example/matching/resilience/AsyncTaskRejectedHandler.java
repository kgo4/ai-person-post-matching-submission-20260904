package com.example.matching.resilience;

import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class AsyncTaskRejectedHandler {

    @ExceptionHandler(TaskRejectedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public R<Void> handleTaskRejectedException(TaskRejectedException e) {
        log.warn("异步任务被拒绝(线程池已满): {}", e.getMessage());
        return R.fail(HttpStatus.SERVICE_UNAVAILABLE.value(),
                "系统繁忙，请稍后再试");
    }
}
