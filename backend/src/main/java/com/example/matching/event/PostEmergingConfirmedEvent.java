package com.example.matching.event;

/** 新兴岗位确认事件 — 发布后由 Closure 域监听处理 */
public record PostEmergingConfirmedEvent(Long postId) {}
