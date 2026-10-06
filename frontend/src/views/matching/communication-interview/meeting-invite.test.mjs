import assert from 'node:assert/strict'
import { HTTP_URL_PATTERN, containsHttpUrl } from './meeting-invite.ts'

/*
 * 讯飞会议「复制邀请信息」的真实形态：多行文本 + 同域名下两条链接
 * （入会链接与客户端下载链接），这正是原先 `new URL(整段文本)` 校验会误杀、
 * 而 `\S+` 正则会吞掉中文的输入。
 */
const INVITE_TEXT = [
  '王永峰 邀请您加入【20260904-2104远程会议】',
  '点击链接直接加入会议: https://meeting.iflyrec.com/meeting/join?HaXa9Nigkb2e',
  '会议号: 11626686 会议密码: 123456',
  '最新版本下载地址: https://meeting.iflyrec.com/download.html',
].join('\n')

// 整段粘贴必须放行
assert.equal(containsHttpUrl(INVITE_TEXT), true)
// 裸链接、http 也要放行
assert.equal(containsHttpUrl('https://meeting.iflyrec.com/meeting/join?HaXa9Nigkb2e'), true)
assert.equal(containsHttpUrl('http://meeting.iflyrec.com/meeting/join?abc'), true)
// 前后空白不影响判定
assert.equal(containsHttpUrl('  https://meeting.iflyrec.com/x  '), true)

// 没有链接的纯文本（只粘了会议号）必须拦下并给出可读原因
assert.equal(containsHttpUrl('会议号: 11626686 会议密码: 123456'), false)
assert.equal(containsHttpUrl(''), false)
assert.equal(containsHttpUrl('   '), false)
assert.equal(containsHttpUrl(null), false)
assert.equal(containsHttpUrl(undefined), false)
// 非 http(s) 协议不算会议链接
assert.equal(containsHttpUrl('ftp://meeting.iflyrec.com/a'), false)
assert.equal(containsHttpUrl('meeting.iflyrec.com/meeting/join?abc'), false)

// 抽出来的第一条必须是入会链接，且不能把后面的中文吞进来
const firstMatch = INVITE_TEXT.match(HTTP_URL_PATTERN)[0]
assert.equal(firstMatch, 'https://meeting.iflyrec.com/meeting/join?HaXa9Nigkb2e')

// 中文紧跟链接（换行被吃掉时）同样要在中文前截断
const glued = '点击链接直接加入会议: https://meeting.iflyrec.com/meeting/join?abc会议号: 11626686'
assert.equal(glued.match(HTTP_URL_PATTERN)[0], 'https://meeting.iflyrec.com/meeting/join?abc')

console.log('meeting invite text tests passed')
