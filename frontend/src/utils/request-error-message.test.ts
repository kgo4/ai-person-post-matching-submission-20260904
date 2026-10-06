import { describe, it, expect } from 'vitest'
import { looksTechnicalText, resolveApiErrorMessage, isCanceledRequest } from './request-error-message'

describe('resolveApiErrorMessage', () => {
  it('优先使用后端业务提示语（HTTP 400 + R.fail 结构）', () => {
    // 这是视频终面的真实场景：会议链接域名不在白名单
    const error = {
      message: 'Request failed with status code 400',
      response: {
        status: 400,
        data: { code: 400, message: '会议链接域名不在允许列表中（当前允许：meeting.iflyrec.com）' },
      },
    }
    expect(resolveApiErrorMessage(error))
      .toBe('会议链接域名不在允许列表中（当前允许：meeting.iflyrec.com）')
  })

  it('绝不再把 axios 英文原文透出给用户', () => {
    const error = {
      message: 'Request failed with status code 400',
      response: { status: 400, data: null },
    }
    const resolved = resolveApiErrorMessage(error)
    expect(resolved).toBe('请求参数有误，请检查后重试')
    expect(resolved).not.toContain('Request failed')
  })

  it('409 状态冲突使用中文兜底', () => {
    const error = { message: 'Request failed with status code 409', response: { status: 409, data: null } }
    expect(resolveApiErrorMessage(error)).toBe('数据状态已变化，请刷新后重试')
  })

  it('后端给了 409 业务文案时以后端为准', () => {
    const error = {
      response: { status: 409, data: { message: '只有待沟通的终面可以取消' } },
    }
    expect(resolveApiErrorMessage(error)).toBe('只有待沟通的终面可以取消')
  })

  it('401 / 403 使用固定中文提示（后端 body 常为空）', () => {
    expect(resolveApiErrorMessage({ response: { status: 401, data: null } }))
      .toBe('登录已过期，请重新登录')
    expect(resolveApiErrorMessage({ response: { status: 403, data: null } }))
      .toBe('没有权限访问该资源')
  })

  it('无响应体时按超时/断网给提示', () => {
    expect(resolveApiErrorMessage({ message: 'timeout of 30000ms exceeded' }))
      .toBe('请求超时，请稍后重试')
    expect(resolveApiErrorMessage({ message: 'Network Error' })).toBe('网络异常，请稍后重试')
  })

  it('请求被取消时可识别，便于调用方静默处理', () => {
    const error = { code: 'ERR_CANCELED', message: 'canceled' }
    expect(isCanceledRequest(error)).toBe(true)
    expect(resolveApiErrorMessage(error)).toBe('请求已取消')
  })

  it('未知结构返回兜底文案而不是抛异常', () => {
    expect(resolveApiErrorMessage(null)).toBe('网络异常，请稍后重试')
    expect(resolveApiErrorMessage(undefined, '自定义兜底')).toBe('自定义兜底')
    expect(resolveApiErrorMessage({}, '自定义兜底')).toBe('自定义兜底')
  })

  /**
   * 这一组是「后端出口闸门漏了」的纵深防御。
   * 真实事故：岗位趋势发现上传材料时，界面上出现了
   * `Duplicate entry '1003-0-1' for key 'rag_knowledge_chunk.uk_doc_chunk_revision'`。
   */
  it('后端把 JDBC 原文当成 message 返回时也不许透出', () => {
    const sql = "### Error updating database. Cause: java.sql.SQLIntegrityConstraintViolationException: "
      + "Duplicate entry '1003-0-1' for key 'rag_knowledge_chunk.uk_doc_chunk_revision'"
    const resolved = resolveApiErrorMessage({ response: { status: 500, data: { message: sql } } })
    expect(resolved).not.toContain('Duplicate entry')
    expect(resolved).not.toContain('rag_knowledge_chunk')
    expect(resolved).toBe('服务器内部错误，请稍后重试')
  })

  it('机器文本识别：强特征命中即判为技术文案', () => {
    expect(looksTechnicalText("Duplicate entry '1-0-1' for key 'uk_doc_chunk_revision'")).toBe(true)
    expect(looksTechnicalText('### The error may exist in com/example/Foo.java')).toBe(true)
    expect(looksTechnicalText('Caused by: java.lang.NullPointerException')).toBe(true)
    expect(looksTechnicalText('at com.example.matching.Foo.bar(Foo.java:42)')).toBe(true)
    expect(looksTechnicalText('Transaction silently rolled back because it has been marked as rollback-only')).toBe(true)
  })

  it('机器文本识别：「中文前缀 + 英文异常原文」的拼接文案同样被拦下', () => {
    // 这正是历史事故的形态：catch 里写 `"文档索引失败: " + e.getMessage()`
    const concatenated = '文档索引失败: ### Error updating database. Cause: '
      + "java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '1003-0-1' for key 'rag_knowledge_chunk.uk_doc_chunk_revision'"
    expect(looksTechnicalText(concatenated)).toBe(true)
  })

  it('机器文本识别：正常中文业务提示不能被误判', () => {
    // 这些文案里带英文词/技术名词，但都是给人看的
    expect(looksTechnicalText('材料已保存，但建立检索索引失败，请稍后在材料管理中重试索引')).toBe(false)
    expect(looksTechnicalText('扫描件或纯图片 PDF 没有文本层，需要先做 OCR 再上传')).toBe(false)
    expect(looksTechnicalText('这份材料此前已经上传过，已直接复用原有索引，没有重复建索引')).toBe(false)
    expect(looksTechnicalText('会议链接域名不在允许列表中（当前允许：meeting.iflyrec.com）')).toBe(false)
    expect(looksTechnicalText('支持的格式：PDF / Word / TXT')).toBe(false)
    // 文件名可能是很长的英文，但这仍是给人看的提示，不能因为「英文长」就被吞掉
    expect(looksTechnicalText('「2026 Global Recruitment Market Trend Analysis Report」上传失败，请检查文件格式'))
      .toBe(false)
  })

  it('机器文本识别：空文本算技术文案（宁可兜底也不给空白）', () => {
    expect(looksTechnicalText('')).toBe(true)
    expect(looksTechnicalText(null)).toBe(true)
    expect(looksTechnicalText(undefined)).toBe(true)
  })

  it('无响应体且 message 是异常原文时落到中文兜底', () => {
    const error = { message: 'java.lang.IllegalStateException: pool exhausted at com.example.A.b(A.java:1)' }
    expect(resolveApiErrorMessage(error)).toBe('网络异常，请稍后重试')
  })

  it('字符串 body 形式的后端提示同样受保护', () => {
    expect(resolveApiErrorMessage({ response: { status: 500, data: 'Internal Server Error' } }))
      .toBe('服务器内部错误，请稍后重试')
  })
})
