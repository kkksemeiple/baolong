/**
 * ============================================================
 * Mock 适配器
 * ------------------------------------------------------------
 * 作用：当 .env 里 VITE_USE_MOCK=true 时，axios 不发出真实网络请求，
 *      全部由这里返回模拟数据。这样没有后端也能把全部页面跑通、演示。
 *
 * 设计要点：
 * 1. 用一张「路由表」匹配 method + path，和真实后端的路由一一对应
 * 2. 数据放在内存里，是「有状态」的：接单后任务状态真的会变，刷新页面才重置
 * 3. 业务失败通过 MockFail 抛出，转成 { code, message } 交给响应拦截器统一提示
 * ============================================================
 */
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { ApiResult } from '@/api/types'

/* ============================================================
 * 工具
 * ============================================================ */

/** 业务失败哨兵：会被转成 code !== 0 的响应 */
class MockFail extends Error {
  code: number

  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

const fail = (message: string, code = 40001) => new MockFail(code, message)

/** 模拟网络延迟，让 loading 状态可见 */
const delay = (ms = 220) => new Promise((resolve) => setTimeout(resolve, ms))

/** N 天前的 ISO 时间字符串 */
const daysAgo = (n: number, hour = 10): string => {
  const d = new Date()
  d.setDate(d.getDate() - n)
  d.setHours(hour, 30, 0, 0)
  return d.toISOString()
}

/** 生成一个纯色圆形头像（SVG data URI），离线可用 */
const avatar = (name: string, color = '#409EFF'): string => {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><rect width="80" height="80" rx="40" fill="${color}"/><text x="40" y="53" font-size="34" fill="#fff" text-anchor="middle" font-family="sans-serif">${name.slice(0, 1)}</text></svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

const clone = <T>(v: T): T =>
  typeof structuredClone === 'function' ? structuredClone(v) : JSON.parse(JSON.stringify(v))

/* ============================================================
 * 内存数据
 * ============================================================ */

let seq = 1000
const nextId = () => ++seq

interface MockUser {
  id: number
  username: string
  /** 角色：USER / ADMIN / SADMIN */
  role: string
  password: string
  nickname: string
  avatar: string
  phone: string
  creditScore: number
  status: string
  authStatus: string
  finishedCount: number
  goodRate: number
  tags: string[]
  createdAt: string
  intro: string
  reportedCount: number
}

const users: MockUser[] = [
  {
    id: 1,
    username: 'linxiaoman',
    role: 'ADMIN',
    password: 'abc12345',
    nickname: '林小满',
    avatar: avatar('林', '#409EFF'),
    phone: '13800138000',
    creditScore: 102,
    status: 'NORMAL',
    authStatus: 'APPROVED',
    finishedCount: 18,
    goodRate: 96,
    tags: ['代取快递', '打印复印', '搬运'],
    createdAt: daysAgo(120),
    intro: '大三在读，课少时间多，手快靠谱。',
    reportedCount: 0,
  },
  {
    id: 2,
    username: 'zhaolei',
    role: 'USER',
    password: 'abc12345',
    nickname: '赵磊',
    avatar: avatar('赵', '#67C23A'),
    phone: '13900139000',
    creditScore: 88,
    status: 'NORMAL',
    authStatus: 'APPROVED',
    finishedCount: 7,
    goodRate: 92,
    tags: ['家教', '编程'],
    createdAt: daysAgo(60),
    intro: '计算机学院，可以辅导数据结构。',
    reportedCount: 0,
  },
  {
    id: 3,
    username: 'chenyu',
    role: 'USER',
    password: 'abc12345',
    nickname: '陈雨',
    avatar: avatar('陈', '#E6A23C'),
    phone: '13700137000',
    creditScore: 55,
    status: 'LIMITED',
    authStatus: 'PENDING',
    finishedCount: 2,
    goodRate: 60,
    tags: [],
    createdAt: daysAgo(15),
    intro: '',
    reportedCount: 2,
  },
]

let tasks = [
  {
    id: 101,
    title: '帮取一下菜鸟驿站的快递',
    description:
      '菜鸟驿站（南门）有两个中等大小的包裹，麻烦帮忙取一下送到 7 号楼 502。箱子上有我的名字，取件码是 8-2-1043。晚上 9 点前都可以。',
    type: 'PICKUP',
    reward: 8,
    deadline: new Date(Date.now() + 36 * 3600_000).toISOString(),
    location: '南门菜鸟驿站 → 7 号楼 502',
    peopleNeeded: 1,
    images: [],
    status: 'OPEN',
    publisherId: 2,
    takerId: null,
    favorited: false,
    mine: false,
    createdAt: daysAgo(0, 9),
    submittedAt: null,
    confirmedAt: null,
  },
  {
    id: 102,
    title: '数据结构期末冲刺辅导（2 小时）',
    description:
      '二叉树遍历和图的算法老是搞不明白，想找人讲一遍重点 + 带着做一套真题。我在图书馆 3 楼，可以约今晚或明晚。',
    type: 'TUTOR',
    reward: 60,
    deadline: new Date(Date.now() + 72 * 3600_000).toISOString(),
    location: '图书馆 3 楼研讨间',
    peopleNeeded: 1,
    images: [],
    status: 'OPEN',
    publisherId: 3,
    takerId: null,
    favorited: true,
    mine: false,
    createdAt: daysAgo(1, 20),
    submittedAt: null,
    confirmedAt: null,
  },
  {
    id: 103,
    title: '代买两杯奶茶送到实验楼',
    description: '实验走不开，麻烦帮忙买两杯珍珠奶茶（少糖），送到实验楼 B404，谢谢！',
    type: 'BUY',
    reward: 6,
    deadline: new Date(Date.now() + 4 * 3600_000).toISOString(),
    location: '商业街 → 实验楼 B404',
    peopleNeeded: 1,
    images: [],
    status: 'OPEN',
    publisherId: 1,
    takerId: null,
    favorited: false,
    mine: true,
    createdAt: daysAgo(0, 8),
    submittedAt: null,
    confirmedAt: null,
  },
  {
    id: 104,
    title: '借用车载小推车搬宿舍用品',
    description: '毕业搬宿舍，想借一个小推车用两小时，用完立刻归还。可以给点押金。',
    type: 'RENT',
    reward: 15,
    deadline: new Date(Date.now() + 48 * 3600_000).toISOString(),
    location: '13 号楼 → 5 号楼',
    peopleNeeded: 1,
    images: [],
    status: 'TAKEN',
    publisherId: 2,
    takerId: 1,
    favorited: false,
    mine: false,
    createdAt: daysAgo(2, 15),
    submittedAt: null,
    confirmedAt: null,
  },
  {
    id: 105,
    title: '帮我打印并装订一份 60 页材料',
    description: '在打印店打一份 PDF，双面黑白，装订成册，送到行政楼 208。文件我会发给你。',
    type: 'OTHER',
    reward: 12,
    deadline: new Date(Date.now() + 24 * 3600_000).toISOString(),
    location: '打印店 → 行政楼 208',
    peopleNeeded: 1,
    images: [],
    status: 'SUBMITTED',
    publisherId: 3,
    takerId: 1,
    favorited: false,
    mine: false,
    createdAt: daysAgo(3, 11),
    submittedAt: daysAgo(0, 9),
    confirmedAt: null,
  },
  {
    id: 106,
    title: '帮忙搬两箱书上五楼',
    description: '两箱书，每箱大概 15 公斤，宿舍楼没电梯，搬到 5 楼。可以两个人一起接。',
    type: 'MOVE',
    reward: 30,
    deadline: new Date(Date.now() + 60 * 3600_000).toISOString(),
    location: '1 号楼 → 5 号楼 501',
    peopleNeeded: 2,
    images: [],
    status: 'COMPLETED',
    publisherId: 2,
    takerId: 1,
    favorited: false,
    mine: false,
    createdAt: daysAgo(5, 14),
    submittedAt: daysAgo(4, 18),
    confirmedAt: daysAgo(4, 20),
  },
  {
    id: 107,
    title: '帮忙排队领校医院体检号',
    description: '明早 7 点半去校医院排队领号，领到告诉我即可，不用陪同。',
    type: 'OTHER',
    reward: 10,
    deadline: daysAgo(1),
    location: '校医院门口',
    peopleNeeded: 1,
    images: [],
    status: 'EXPIRED',
    publisherId: 1,
    takerId: null,
    favorited: false,
    mine: true,
    createdAt: daysAgo(6, 8),
    submittedAt: null,
    confirmedAt: null,
  },
  {
    id: 108,
    title: '帮忙带一份晚饭到自习室',
    description: '在二食堂帮我打一份饭菜，送到新图书馆 4 楼自习区，我在靠窗位置。',
    type: 'BUY',
    reward: 7,
    deadline: new Date(Date.now() + 6 * 3600_000).toISOString(),
    location: '二食堂 → 新图书馆 4 楼',
    peopleNeeded: 1,
    images: [],
    status: 'DISPUTED',
    publisherId: 1,
    takerId: 3,
    favorited: false,
    mine: true,
    createdAt: daysAgo(2, 17),
    submittedAt: daysAgo(2, 19),
    confirmedAt: null,
  },
]

let conversations = [
  {
    id: 201,
    taskId: 104,
    participantAId: 2,
    participantBId: 1,
    lastMessage: '小推车我已经放在 13 号楼门口了，你直接来拿就行～',
    lastMessageAt: daysAgo(0, 11),
    unreadCount: 2,
  },
  {
    id: 202,
    taskId: 105,
    participantAId: 3,
    participantBId: 1,
    lastMessage: '材料已经打印好啦，现在就送去行政楼。',
    lastMessageAt: daysAgo(0, 10),
    unreadCount: 0,
  },
]

let messages = [
  {
    id: 301,
    conversationId: 201,
    fromUserId: 2,
    content: '你好，小推车可以借，你什么时候要？',
    contentType: 'TEXT',
    readFlag: true,
    createdAt: daysAgo(1, 16),
  },
  {
    id: 302,
    conversationId: 201,
    fromUserId: 1,
    content: '明天上午十点左右可以吗？用完我下午就还。',
    contentType: 'TEXT',
    readFlag: true,
    createdAt: daysAgo(1, 16),
  },
  {
    id: 303,
    conversationId: 201,
    fromUserId: 2,
    content: '没问题，那就明天十点。',
    contentType: 'TEXT',
    readFlag: true,
    createdAt: daysAgo(1, 17),
  },
  {
    id: 304,
    conversationId: 201,
    fromUserId: 2,
    content: '小推车我已经放在 13 号楼门口了，你直接来拿就行～',
    contentType: 'TEXT',
    readFlag: false,
    createdAt: daysAgo(0, 11),
  },
  {
    id: 305,
    conversationId: 202,
    fromUserId: 1,
    content: '材料发你邮箱了，双面黑白装订成册就行。',
    contentType: 'TEXT',
    readFlag: true,
    createdAt: daysAgo(0, 9),
  },
  {
    id: 306,
    conversationId: 202,
    fromUserId: 3,
    content: '材料已经打印好啦，现在就送去行政楼。',
    contentType: 'TEXT',
    readFlag: true,
    createdAt: daysAgo(0, 10),
  },
]

let notifications = [
  {
    id: 401,
    type: 'TASK',
    title: '任务待你确认',
    content: '「帮我打印并装订一份 60 页材料」已提交完成，请及时确认。',
    bizId: 105,
    link: '/tasks/105',
    readFlag: false,
    createdAt: daysAgo(0, 9),
  },
  {
    id: 402,
    type: 'FUND',
    title: '报酬已到账',
    content: '任务「帮忙搬两箱书上五楼」已完成，+30 WB 已到账。',
    bizId: 106,
    link: '/wallet',
    readFlag: false,
    createdAt: daysAgo(4, 20),
  },
  {
    id: 403,
    type: 'FORUM',
    title: '有人回复了你的帖子',
    content: '赵磊回复了「期末复习有什么经验可以分享？」。',
    bizId: 501,
    link: '/forum/501',
    readFlag: true,
    createdAt: daysAgo(1, 21),
  },
  {
    id: 404,
    type: 'SYSTEM',
    title: '实名认证已通过',
    content: '你的学号实名认证已通过，现在可以发布任务了。',
    bizId: null,
    link: '/profile',
    readFlag: true,
    createdAt: daysAgo(20, 12),
  },
  {
    id: 405,
    type: 'TASK',
    title: '任务即将截止',
    content: '「代买两杯奶茶送到实验楼」还有 4 小时截止。',
    bizId: 103,
    link: '/tasks/103',
    readFlag: false,
    createdAt: daysAgo(0, 8),
  },
]

let wallet = { userId: 1, available: 320, frozen: 80, debt: 0 }

let transactions = [
  {
    id: 601,
    userId: 1,
    type: 'INCOME',
    amount: 30,
    balanceAfter: 320,
    bizType: 'TASK',
    bizId: 106,
    bizTitle: '帮忙搬两箱书上五楼',
    remark: '任务完成收入',
    createdAt: daysAgo(4, 20),
  },
  {
    id: 602,
    userId: 1,
    type: 'FREEZE',
    amount: -7,
    balanceAfter: 290,
    bizType: 'TASK',
    bizId: 108,
    bizTitle: '帮忙带一份晚饭到自习室',
    remark: '发布任务冻结',
    createdAt: daysAgo(2, 17),
  },
  {
    id: 603,
    userId: 1,
    type: 'PENALTY',
    amount: -5,
    balanceAfter: 290,
    bizType: 'TASK',
    bizId: 107,
    bizTitle: '帮忙排队领校医院体检号',
    remark: '任务过期扣除违约金',
    createdAt: daysAgo(1, 8),
  },
  {
    id: 604,
    userId: 1,
    type: 'GRANT',
    amount: 100,
    balanceAfter: 295,
    bizType: 'REGISTER',
    bizId: null,
    bizTitle: null,
    remark: '新用户注册赠送',
    createdAt: daysAgo(120, 9),
  },
  {
    id: 605,
    userId: 1,
    type: 'INCOME',
    amount: 12,
    balanceAfter: 195,
    bizType: 'TASK',
    bizId: 105,
    bizTitle: '帮我打印并装订一份 60 页材料',
    remark: '任务完成收入',
    createdAt: daysAgo(10, 16),
  },
  {
    id: 606,
    userId: 1,
    type: 'FREEZE',
    amount: -6,
    balanceAfter: 183,
    bizType: 'TASK',
    bizId: 103,
    bizTitle: '代买两杯奶茶送到实验楼',
    remark: '发布任务冻结',
    createdAt: daysAgo(0, 8),
  },
]

let frozenItems = [
  { taskId: 103, taskTitle: '代买两杯奶茶送到实验楼', amount: 6, frozenAt: daysAgo(0, 8) },
  { taskId: 108, taskTitle: '帮忙带一份晚饭到自习室', amount: 7, frozenAt: daysAgo(2, 17) },
  { taskId: 110, taskTitle: '待发布的其他委托（示例）', amount: 67, frozenAt: daysAgo(3, 9) },
]

let favorites = [
  {
    id: 701,
    targetType: 'TASK',
    targetId: 102,
    title: '数据结构期末冲刺辅导（2 小时）',
    summary: '图书馆 3 楼研讨间 · 报酬 60 WB',
    invalid: false,
    createdAt: daysAgo(1, 21),
  },
  {
    id: 702,
    targetType: 'POST',
    targetId: 501,
    title: '期末复习有什么经验可以分享？',
    summary: '学习交流 · 12 条回复',
    invalid: false,
    createdAt: daysAgo(3, 10),
  },
  {
    id: 703,
    targetType: 'TASK',
    targetId: 107,
    title: '帮忙排队领校医院体检号',
    summary: '已过期',
    invalid: true,
    createdAt: daysAgo(6, 9),
  },
]

let history = [
  {
    id: 801,
    targetType: 'TASK',
    targetId: 102,
    title: '数据结构期末冲刺辅导（2 小时）',
    viewedAt: daysAgo(0, 12),
  },
  {
    id: 802,
    targetType: 'POST',
    targetId: 501,
    title: '期末复习有什么经验可以分享？',
    viewedAt: daysAgo(0, 11),
  },
  {
    id: 803,
    targetType: 'TASK',
    targetId: 101,
    title: '帮取一下菜鸟驿站的快递',
    viewedAt: daysAgo(1, 19),
  },
]

let posts = [
  {
    id: 501,
    title: '期末复习有什么经验可以分享？',
    content:
      '感觉这学期课特别多，复习时间根本不够。想问问大家都是怎么安排复习节奏的？有没有什么好用的方法或者工具推荐？',
    authorId: 2,
    tags: ['学习交流'],
    replyCount: 2,
    likeCount: 12,
    liked: false,
    favorited: true,
    createdAt: daysAgo(3, 10),
  },
  {
    id: 502,
    title: '求助：宿舍楼下共享单车总是被锁死怎么办',
    content:
      '好几次扫码之后提示车辆故障，但车其实好好的，就是停在原地不动。有没有同学遇到过？该怎么处理比较合适？',
    authorId: 3,
    tags: ['生活求助'],
    replyCount: 0,
    likeCount: 5,
    liked: false,
    favorited: false,
    createdAt: daysAgo(2, 15),
  },
  {
    id: 503,
    title: '分享：我用万事屋赚了第一笔屋币',
    content:
      '上周末接了三单代取快递，一单 8 币，加起来也够喝两天奶茶了。感觉平台还挺好用的，希望多一些人发任务哈哈。',
    authorId: 1,
    tags: ['经验分享'],
    replyCount: 1,
    likeCount: 23,
    liked: true,
    favorited: false,
    createdAt: daysAgo(5, 20),
  },
]

let replies = [
  {
    id: 901,
    postId: 501,
    parentId: null,
    content: '我一般提前两周开始，先把课件过一遍，再刷真题。效率比死背高很多。',
    authorId: 1,
    createdAt: daysAgo(2, 9),
  },
  {
    id: 902,
    postId: 501,
    parentId: 901,
    content: '提前两周是认真的吗……我一般提前两天（',
    authorId: 3,
    createdAt: daysAgo(2, 11),
  },
  {
    id: 903,
    postId: 503,
    parentId: null,
    content: '哈哈哈学到了，我也去接两单试试。',
    authorId: 2,
    createdAt: daysAgo(4, 18),
  },
]

let audits = [
  {
    id: 1001,
    targetType: 'TASK',
    targetId: 110,
    title: '代写课程设计报告',
    content: '要求：帮我写一份软件工程课程设计报告，30 页左右，价格好商量。',
    hitReason: '命中违禁词：代写',
    author: { id: 3, nickname: '陈雨', avatar: avatar('陈', '#E6A23C'), creditScore: 55 },
    status: 'PENDING',
    createdAt: daysAgo(0, 13),
  },
  {
    id: 1002,
    targetType: 'POST',
    targetId: 504,
    title: '低价出售考研资料（含盗版网课）',
    content: '全套考研网课，包含某某机构内部视频，价格便宜，需要的私聊。',
    hitReason: '命中违禁词：盗版',
    author: { id: 2, nickname: '赵磊', avatar: avatar('赵', '#67C23A'), creditScore: 88 },
    status: 'PENDING',
    createdAt: daysAgo(1, 10),
  },
  {
    id: 1003,
    targetType: 'REPLY',
    targetId: 905,
    title: '回复内容含广告',
    content: '加我微信 xxxx 低价代做毕设，保证过。',
    hitReason: '命中违禁词：代做',
    author: { id: 3, nickname: '陈雨', avatar: avatar('陈', '#E6A23C'), creditScore: 55 },
    status: 'PENDING',
    createdAt: daysAgo(1, 16),
  },
]

let reports = [
  {
    id: 1101,
    type: 'NO_SHOW',
    taskId: 108,
    postId: null,
    targetTitle: '帮忙带一份晚饭到自习室',
    reporter: { id: 1, nickname: '林小满', avatar: avatar('林', '#409EFF'), creditScore: 102 },
    reason: '接单后一直没送到，联系也不回，饭点都过了。',
    evidence: [],
    status: 'PENDING',
    result: null,
    createdAt: daysAgo(0, 12),
  },
  {
    id: 1102,
    type: 'ILLEGAL_CONTENT',
    taskId: 110,
    postId: null,
    targetTitle: '代写课程设计报告',
    reporter: { id: 2, nickname: '赵磊', avatar: avatar('赵', '#67C23A'), creditScore: 88 },
    reason: '这类代写任务违反学校规定，应该下架。',
    evidence: [],
    status: 'PENDING',
    result: null,
    createdAt: daysAgo(0, 14),
  },
]

let disputes = [
  {
    id: 1201,
    taskId: 108,
    taskTitle: '帮忙带一份晚饭到自习室',
    publisher: { id: 1, nickname: '林小满', avatar: avatar('林', '#409EFF'), creditScore: 102 },
    taker: { id: 3, nickname: '陈雨', avatar: avatar('陈', '#E6A23C'), creditScore: 55 },
    frozenAmount: 7,
    initiatorId: 1,
    reason: '接单方超时未送达，联系不上，申请退款。',
    evidence: [],
    status: 'PENDING',
    result: null,
    remark: null,
    createdAt: daysAgo(0, 12),
  },
  {
    id: 1202,
    taskId: 106,
    taskTitle: '帮忙搬两箱书上五楼',
    publisher: { id: 2, nickname: '赵磊', avatar: avatar('赵', '#67C23A'), creditScore: 88 },
    taker: { id: 1, nickname: '林小满', avatar: avatar('林', '#409EFF'), creditScore: 102 },
    frozenAmount: 30,
    initiatorId: 1,
    reason: '已经搬完并拍照了，委托方一直不确认，申请介入。',
    evidence: [],
    status: 'JUDGED',
    result: 'TAKER',
    remark: '接单方提供了搬运完成的照片证据，支持全额支付。',
    createdAt: daysAgo(4, 21),
  },
]

const systemConfig = {
  initGrant: 100,
  maxHolding: 10000,
  grantLimit: 500,
  autoConfirmHours: 72,
  penaltyCancel: 5,
  penaltyNoShow: 20,
}

const operationLogs = [
  {
    id: 1301,
    operator: '张皓宁',
    action: '封禁用户',
    target: '陈雨（chenyu）',
    before: '正常',
    after: '封禁',
    reason: '多次违规发布代写任务',
    createdAt: daysAgo(1, 15),
  },
  {
    id: 1302,
    operator: '张皓宁',
    action: '下架任务',
    target: '代写课程设计报告',
    before: '待接取',
    after: '已下架',
    reason: '违反学术诚信规定',
    createdAt: daysAgo(1, 16),
  },
]

/* ============================================================
 * 当前登录用户（Mock 里用变量模拟"已登录的用户"）
 * ============================================================ */
let currentUserId: number | null = null

const findUser = (id: number) => users.find((u) => u.id === id)
const publicUser = (u: MockUser | undefined) => {
  if (!u) return null
  const { password: _password, reportedCount: _reportedCount, ...rest } = u
  return {
    ...rest,
    phone: `${u.phone.slice(0, 3)}****${u.phone.slice(7)}`,
  }
}
const brief = (id: number) => {
  const u = findUser(id)
  return u ? { id: u.id, nickname: u.nickname, avatar: u.avatar, creditScore: u.creditScore } : undefined
}

/** 给任务补充关联展示字段 */
const decorateTask = (t: (typeof tasks)[number]) => ({
  ...clone(t),
  publisher: brief(t.publisherId),
  taker: t.takerId ? brief(t.takerId) : null,
})

/* ============================================================
 * 路由表
 * ============================================================ */

type Ctx = {
  m: RegExpMatchArray
  method: string
  query: Record<string, string>
  body: Record<string, any>
  user: MockUser | null
}
type Handler = (ctx: Ctx) => any

const requireLogin = (ctx: Ctx): MockUser => {
  if (!ctx.user) throw new MockFail('请先登录', 40100)
  return ctx.user
}

const routes: Array<{ method: string; pattern: RegExp; handler: Handler }> = [
  /* ---------------- 认证 ---------------- */
  {
    method: 'POST',
    pattern: /^\/auth\/register$/,
    handler: ({ body }) => {
      if (!body.username || !body.password) throw fail('账号和密码不能为空')
      if (users.some((u) => u.username === body.username)) throw fail('该账号已被注册')
      const u: MockUser = {
        id: nextId(),
        username: body.username,
        role: 'USER',
        password: body.password,
        nickname: body.nickname || body.username,
        avatar: avatar(body.nickname || body.username),
        phone: body.phone,
        creditScore: 100,
        status: 'NORMAL',
        authStatus: 'NONE',
        finishedCount: 0,
        goodRate: 100,
        tags: [],
        createdAt: new Date().toISOString(),
        intro: '',
        reportedCount: 0,
      }
      users.push(u)
      currentUserId = u.id
      return { token: `mock-token-${u.id}`, expiresIn: 604800, user: publicUser(u) }
    },
  },
  {
    method: 'POST',
    pattern: /^\/auth\/login$/,
    handler: ({ body }) => {
      const u = users.find((x) => x.username === body.username)
      if (!u || u.password !== body.password) throw fail('账号或密码错误')
      if (u.status === 'BANNED') throw fail('该账号已被封禁')
      currentUserId = u.id
      return { token: `mock-token-${u.id}`, expiresIn: 604800, user: publicUser(u) }
    },
  },
  { method: 'POST', pattern: /^\/auth\/logout$/, handler: () => { currentUserId = null } },
  {
    method: 'GET',
    pattern: /^\/auth\/me$/,
    handler: (ctx) => publicUser(requireLogin(ctx)),
  },
  {
    method: 'POST',
    pattern: /^\/auth\/password\/forgot$/,
    handler: () => ({ expireIn: 900 }),
  },
  {
    method: 'POST',
    pattern: /^\/auth\/password\/reset$/,
    handler: ({ body }) => {
      if (body.code !== '123456') throw fail('验证码不正确')
    },
  },
  {
    method: 'PUT',
    pattern: /^\/auth\/password$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      if (u.password !== ctx.body.oldPassword) throw fail('原密码不正确')
      u.password = ctx.body.newPassword
    },
  },
  {
    method: 'GET',
    pattern: /^\/auth\/student-cert$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      return { authStatus: u.authStatus, studentNo: u.authStatus === 'APPROVED' ? '2023012345' : null, rejectReason: null }
    },
  },
  {
    method: 'POST',
    pattern: /^\/auth\/student-cert$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      if (!ctx.body.studentNo || !ctx.body.certImage) throw fail('学号和学生证照片不能为空')
      u.authStatus = 'PENDING'
    },
  },
  {
    method: 'GET',
    pattern: /^\/auth\/login-logs$/,
    handler: (ctx) => {
      requireLogin(ctx)
      return [
        { id: 1, ip: '10.12.34.56', location: '校内 · 图书馆', userAgent: 'Chrome 126 / Windows', success: true, failReason: null, loginAt: daysAgo(0, 9) },
        { id: 2, ip: '10.12.34.56', location: '校内 · 图书馆', userAgent: 'Chrome 126 / Windows', success: true, failReason: null, loginAt: daysAgo(1, 8) },
        { id: 3, ip: '117.136.12.9', location: '校外 · 西安', userAgent: 'Safari / iPhone', success: true, failReason: null, loginAt: daysAgo(3, 21) },
        { id: 4, ip: '10.12.34.56', location: '校内 · 宿舍', userAgent: 'Chrome 126 / Windows', success: false, failReason: '密码错误', loginAt: daysAgo(5, 22) },
      ]
    },
  },

  /* ---------------- 文件上传 ---------------- */
  {
    method: 'POST',
    pattern: /^\/files\/upload$/,
    handler: () => ({ url: avatar('图', '#909399') }),
  },

  /* ---------------- 任务 ---------------- */
  {
    method: 'GET',
    pattern: /^\/tasks\/categories$/,
    handler: () => [
      { id: 1, name: '代取快递', icon: 'Box' },
      { id: 2, name: '代买', icon: 'ShoppingCart' },
      { id: 3, name: '租赁', icon: 'Files' },
      { id: 4, name: '家教', icon: 'Reading' },
      { id: 5, name: '搬运', icon: 'Van' },
      { id: 6, name: '其他', icon: 'More' },
    ],
  },
  {
    method: 'GET',
    pattern: /^\/tasks\/search$/,
    handler: ({ query }) => {
      const kw = (query.keyword || '').toLowerCase()
      const list = tasks.filter(
        (t) => t.status === 'OPEN' && (t.title.toLowerCase().includes(kw) || t.description.toLowerCase().includes(kw)),
      )
      return { list: list.map(decorateTask), nextCursor: null, hasMore: false }
    },
  },
  {
    method: 'GET',
    pattern: /^\/tasks\/recommend$/,
    handler: () => tasks.filter((t) => t.status === 'OPEN').slice(0, 3).map(decorateTask),
  },
  {
    method: 'GET',
    pattern: /^\/tasks\/mine$/,
    handler: ({ query }) => {
      const role = query.role === 'taker' ? 'taker' : 'publisher'
      const uid = currentUserId ?? 1
      let list = tasks.filter((t) => (role === 'taker' ? t.takerId === uid : t.publisherId === uid))
      if (query.status) list = list.filter((t) => t.status === query.status)
      return { list: list.map(decorateTask), nextCursor: null, hasMore: false }
    },
  },
  {
    method: 'GET',
    pattern: /^\/tasks$/,
    handler: ({ query }) => {
      let list = tasks.filter((t) => t.status === 'OPEN')
      if (query.type) list = list.filter((t) => t.type === query.type)
      if (query.minReward) list = list.filter((t) => t.reward >= Number(query.minReward))
      if (query.maxReward) list = list.filter((t) => t.reward <= Number(query.maxReward))
      if (query.sortBy === 'REWARD') list = [...list].sort((a, b) => b.reward - a.reward)
      if (query.sortBy === 'DEADLINE')
        list = [...list].sort((a, b) => +new Date(a.deadline) - +new Date(b.deadline))
      return { list: list.map(decorateTask), nextCursor: null, hasMore: false }
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      if (!ctx.body.title || !ctx.body.reward) throw fail('标题和报酬不能为空')
      if (wallet.available < ctx.body.reward) throw fail('余额不足，请先赚取更多屋币')
      wallet.available -= ctx.body.reward
      wallet.frozen += ctx.body.reward
      const t = {
        id: nextId(),
        title: ctx.body.title,
        description: ctx.body.description || '',
        type: ctx.body.type || 'OTHER',
        reward: ctx.body.reward,
        deadline: ctx.body.deadline || daysAgo(-3),
        location: ctx.body.location || '',
        peopleNeeded: ctx.body.peopleNeeded || 1,
        images: ctx.body.images || [],
        status: 'OPEN',
        publisherId: u.id,
        takerId: null,
        favorited: false,
        mine: true,
        createdAt: new Date().toISOString(),
        submittedAt: null,
        confirmedAt: null,
      }
      tasks = [t as any, ...tasks]
      return decorateTask(t as any)
    },
  },
  {
    method: 'GET',
    pattern: /^\/tasks\/(\d+)$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      return decorateTask(t)
    },
  },
  {
    method: 'PUT',
    pattern: /^\/tasks\/(\d+)$/,
    handler: ({ m, body }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      Object.assign(t, body)
      return decorateTask(t)
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/cancel$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      if (t.status !== 'OPEN') throw fail('只有待接取的任务可以取消')
      t.status = 'CANCELLED'
      wallet.frozen = Math.max(0, wallet.frozen - t.reward)
      wallet.available += t.reward
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/claim$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      const t = tasks.find((x) => x.id === Number(ctx.m[1]))
      if (!t) throw fail('任务不存在')
      if (t.status !== 'OPEN') throw fail('任务已被接取')
      if (t.publisherId === u.id) throw fail('不能接取自己发布的任务')
      if (u.authStatus !== 'APPROVED') throw fail('请先完成学号实名认证')
      if (u.creditScore < 60) throw fail('信用分不足 60，暂时无法接单')
      t.status = 'TAKEN'
      t.takerId = u.id
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/abandon$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      if (t.status !== 'TAKEN') throw fail('当前状态不能放弃')
      t.status = 'OPEN'
      t.takerId = null
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/submit$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      if (t.status !== 'TAKEN') throw fail('当前状态不能提交')
      t.status = 'SUBMITTED'
      t.submittedAt = new Date().toISOString()
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/confirm$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      if (t.status !== 'SUBMITTED') throw fail('当前状态不能确认')
      t.status = 'SETTLED'
      t.confirmedAt = new Date().toISOString()
      // 冻结 → 划转给接单方
      wallet.frozen = Math.max(0, wallet.frozen - t.reward)
      transactions = [
        {
          id: nextId(),
          userId: 1,
          type: 'INCOME',
          amount: t.reward,
          balanceAfter: wallet.available,
          bizType: 'TASK',
          bizId: t.id,
          bizTitle: t.title,
          remark: '任务完成收入',
          createdAt: new Date().toISOString(),
        } as any,
        ...transactions,
      ]
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/reject$/,
    handler: ({ m, body }) => {
      if (!body.reason) throw fail('请填写拒绝理由')
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      t.status = 'TAKEN'
      t.submittedAt = null
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/dispute$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      t.status = 'DISPUTED'
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/bargain$/,
    handler: ({ m, body }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (!t) throw fail('任务不存在')
      if (!body.amount || body.amount <= 0) throw fail('请输入有效的议价金额')
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/bargain\/respond$/,
    handler: () => undefined,
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/favorite$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (t) t.favorited = true
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/tasks\/(\d+)\/favorite$/,
    handler: ({ m }) => {
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (t) t.favorited = false
    },
  },
  {
    method: 'POST',
    pattern: /^\/tasks\/(\d+)\/report$/,
    handler: () => undefined,
  },

  /* ---------------- 消息 ---------------- */
  {
    method: 'GET',
    pattern: /^\/conversations$/,
    handler: () => {
      const uid = currentUserId ?? 1
      return conversations
        .filter((c) => c.participantAId === uid || c.participantBId === uid)
        .map((c) => {
          const t = tasks.find((x) => x.id === c.taskId)
          const peerId = c.participantAId === uid ? c.participantBId : c.participantAId
          return {
            id: c.id,
            taskId: c.taskId,
            taskTitle: t?.title ?? '',
            peer: brief(peerId),
            lastMessage: c.lastMessage,
            lastMessageAt: c.lastMessageAt,
            unreadCount: c.unreadCount,
          }
        })
        .sort((a, b) => +new Date(b.lastMessageAt) - +new Date(a.lastMessageAt))
    },
  },
  {
    method: 'GET',
    pattern: /^\/conversations\/by-task\/(\d+)$/,
    handler: (ctx) => {
      const uid = requireLogin(ctx).id
      const taskId = Number(ctx.m[1])
      let c = conversations.find((x) => x.taskId === taskId)
      if (!c) {
        const t = tasks.find((x) => x.id === taskId)
        if (!t) throw fail('任务不存在')
        c = {
          id: nextId(),
          taskId,
          participantAId: t.publisherId,
          participantBId: uid,
          lastMessage: '',
          lastMessageAt: new Date().toISOString(),
          unreadCount: 0,
        } as any
        conversations.push(c)
      }
      const t = tasks.find((x) => x.id === taskId)
      const peerId = c.participantAId === uid ? c.participantBId : c.participantAId
      return {
        id: c.id,
        taskId,
        taskTitle: t?.title ?? '',
        peer: brief(peerId),
        lastMessage: c.lastMessage,
        lastMessageAt: c.lastMessageAt,
        unreadCount: c.unreadCount,
      }
    },
  },
  {
    method: 'GET',
    pattern: /^\/conversations\/(\d+)\/messages$/,
    handler: ({ m }) => {
      const id = Number(m[1])
      const list = messages.filter((x) => x.conversationId === id)
      return { list: clone(list), nextCursor: null, hasMore: false }
    },
  },
  {
    method: 'POST',
    pattern: /^\/conversations\/(\d+)\/messages$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      const cid = Number(ctx.m[1])
      const msg = {
        id: nextId(),
        conversationId: cid,
        fromUserId: u.id,
        content: ctx.body.content,
        contentType: ctx.body.contentType || 'TEXT',
        readFlag: false,
        createdAt: new Date().toISOString(),
      }
      messages.push(msg as any)
      const c = conversations.find((x) => x.id === cid)
      if (c) {
        c.lastMessage = ctx.body.content
        c.lastMessageAt = msg.createdAt
      }
      return msg
    },
  },
  {
    method: 'POST',
    pattern: /^\/conversations\/(\d+)\/read$/,
    handler: ({ m }) => {
      const cid = Number(m[1])
      messages.filter((x) => x.conversationId === cid).forEach((x) => (x.readFlag = true))
      const c = conversations.find((x) => x.id === cid)
      if (c) c.unreadCount = 0
    },
  },

  /* ---------------- 通知 ---------------- */
  {
    method: 'GET',
    pattern: /^\/notifications\/unread-count$/,
    handler: () => {
      const unread = notifications.filter((n) => !n.readFlag)
      const pick = (t: string) => unread.filter((n) => n.type === t).length
      return {
        task: pick('TASK'),
        fund: pick('FUND'),
        forum: pick('FORUM'),
        system: pick('SYSTEM'),
        total: unread.length,
      }
    },
  },
  {
    method: 'GET',
    pattern: /^\/notifications$/,
    handler: ({ query }) => {
      let list = [...notifications]
      if (query.type) list = list.filter((n) => n.type === query.type)
      if (query.unreadOnly === 'true') list = list.filter((n) => !n.readFlag)
      list.sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
      return { list: clone(list), nextCursor: null, hasMore: false }
    },
  },
  {
    method: 'POST',
    pattern: /^\/notifications\/read$/,
    handler: ({ body }) => {
      const ids: number[] = body.ids || []
      notifications.forEach((n) => {
        if (ids.includes(n.id)) n.readFlag = true
      })
    },
  },
  {
    method: 'POST',
    pattern: /^\/notifications\/read-all$/,
    handler: () => {
      notifications.forEach((n) => (n.readFlag = true))
    },
  },

  /* ---------------- 论坛 ---------------- */
  {
    method: 'GET',
    pattern: /^\/posts$/,
    handler: ({ query }) => {
      let list = [...posts]
      if (query.tag) list = list.filter((p) => p.tags.includes(query.tag))
      if (query.keyword) {
        const kw = query.keyword.toLowerCase()
        list = list.filter((p) => p.title.toLowerCase().includes(kw) || p.content.toLowerCase().includes(kw))
      }
      if (query.sortBy === 'HOT') list.sort((a, b) => b.likeCount - a.likeCount)
      else list.sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
      return {
        list: list.map((p) => ({ ...clone(p), author: brief(p.authorId) })),
        nextCursor: null,
        hasMore: false,
      }
    },
  },
  {
    method: 'POST',
    pattern: /^\/posts$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      if (!ctx.body.title) throw fail('标题不能为空')
      if (String(ctx.body.content).includes('代写')) throw fail('内容包含违禁词：代写')
      const p = {
        id: nextId(),
        title: ctx.body.title,
        content: ctx.body.content,
        authorId: u.id,
        tags: ctx.body.tags || [],
        replyCount: 0,
        likeCount: 0,
        liked: false,
        favorited: false,
        createdAt: new Date().toISOString(),
      }
      posts.unshift(p as any)
      return { ...p, author: brief(u.id) }
    },
  },
  {
    method: 'GET',
    pattern: /^\/posts\/(\d+)$/,
    handler: ({ m }) => {
      const p = posts.find((x) => x.id === Number(m[1]))
      if (!p) throw fail('帖子不存在')
      history.unshift({
        id: nextId(),
        targetType: 'POST',
        targetId: p.id,
        title: p.title,
        viewedAt: new Date().toISOString(),
      } as any)
      return { ...clone(p), author: brief(p.authorId) }
    },
  },
  {
    method: 'PUT',
    pattern: /^\/posts\/(\d+)$/,
    handler: ({ m, body }) => {
      const p = posts.find((x) => x.id === Number(m[1]))
      if (!p) throw fail('帖子不存在')
      Object.assign(p, body)
      return { ...clone(p), author: brief(p.authorId) }
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/posts\/(\d+)$/,
    handler: ({ m }) => {
      posts = posts.filter((x) => x.id !== Number(m[1]))
      replies = replies.filter((x) => x.postId !== Number(m[1]))
    },
  },
  {
    method: 'GET',
    pattern: /^\/posts\/(\d+)\/replies$/,
    handler: ({ m }) => {
      const pid = Number(m[1])
      const all = replies.filter((x) => x.postId === pid)
      const roots = all.filter((x) => !x.parentId)
      return roots.map((r) => ({
        ...clone(r),
        author: brief(r.authorId),
        children: all
          .filter((c) => c.parentId === r.id)
          .map((c) => ({ ...clone(c), author: brief(c.authorId) })),
      }))
    },
  },
  {
    method: 'POST',
    pattern: /^\/posts\/(\d+)\/replies$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      const pid = Number(ctx.m[1])
      const r = {
        id: nextId(),
        postId: pid,
        parentId: ctx.body.parentId ?? null,
        content: ctx.body.content,
        authorId: u.id,
        createdAt: new Date().toISOString(),
      }
      replies.push(r as any)
      const p = posts.find((x) => x.id === pid)
      if (p) p.replyCount += 1
      return { ...r, author: brief(u.id) }
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/replies\/(\d+)$/,
    handler: ({ m }) => {
      replies = replies.filter((x) => x.id !== Number(m[1]))
    },
  },
  {
    method: 'POST',
    pattern: /^\/posts\/(\d+)\/like$/,
    handler: ({ m }) => {
      const p = posts.find((x) => x.id === Number(m[1]))
      if (p && !p.liked) {
        p.liked = true
        p.likeCount += 1
      }
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/posts\/(\d+)\/like$/,
    handler: ({ m }) => {
      const p = posts.find((x) => x.id === Number(m[1]))
      if (p && p.liked) {
        p.liked = false
        p.likeCount = Math.max(0, p.likeCount - 1)
      }
    },
  },
  {
    method: 'POST',
    pattern: /^\/posts\/(\d+)\/favorite$/,
    handler: ({ m }) => {
      const p = posts.find((x) => x.id === Number(m[1]))
      if (p) p.favorited = true
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/posts\/(\d+)\/favorite$/,
    handler: ({ m }) => {
      const p = posts.find((x) => x.id === Number(m[1]))
      if (p) p.favorited = false
    },
  },
  {
    method: 'POST',
    pattern: /^\/posts\/(\d+)\/report$/,
    handler: () => undefined,
  },

  /* ---------------- 用户与个人中心 ---------------- */
  {
    method: 'PUT',
    pattern: /^\/users\/me$/,
    handler: (ctx) => {
      const u = requireLogin(ctx)
      Object.assign(u, ctx.body)
      return publicUser(u)
    },
  },
  {
    method: 'GET',
    pattern: /^\/users\/(\d+)$/,
    handler: ({ m }) => publicUser(findUser(Number(m[1]))),
  },
  {
    method: 'GET',
    pattern: /^\/me\/favorites$/,
    handler: ({ query }) => {
      let list = [...favorites]
      if (query.targetType) list = list.filter((x) => x.targetType === query.targetType)
      return { list: clone(list), nextCursor: null, hasMore: false }
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/me\/favorites$/,
    handler: ({ body }) => {
      favorites = favorites.filter(
        (x) => !(x.targetType === body.targetType && x.targetId === body.targetId),
      )
    },
  },
  {
    method: 'GET',
    pattern: /^\/me\/history$/,
    handler: () => ({ list: clone(history), nextCursor: null, hasMore: false }),
  },
  {
    method: 'DELETE',
    pattern: /^\/me\/history\/(\d+)$/,
    handler: ({ m }) => {
      history = history.filter((x) => x.id !== Number(m[1]))
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/me\/history$/,
    handler: () => {
      history = []
    },
  },
  {
    method: 'GET',
    pattern: /^\/me\/wallet$/,
    handler: () => ({
      ...wallet,
      total: wallet.available + wallet.frozen - wallet.debt,
    }),
  },
  {
    method: 'GET',
    pattern: /^\/me\/wallet\/transactions$/,
    handler: ({ query }) => {
      let list = [...transactions]
      if (query.type) list = list.filter((x) => x.type === query.type)
      list.sort((a, b) => +new Date(b.createdAt) - +new Date(a.createdAt))
      return { list: clone(list), nextCursor: null, hasMore: false }
    },
  },
  {
    method: 'GET',
    pattern: /^\/me\/wallet\/frozen$/,
    handler: () => clone(frozenItems),
  },

  /* ---------------- 管理后台 ---------------- */
  {
    method: 'GET',
    pattern: /^\/admin\/stats\/overview$/,
    handler: () => ({
      userCount: users.length + 38,
      taskCount: tasks.length + 92,
      postCount: posts.length + 61,
      finishRate: 78,
      disputeRate: 4,
      coinIssued: 12600,
      coinCirculating: 8420,
      coinFrozen: 380,
      coinRecycled: 3800,
      coinDebt: 15,
    }),
  },
  {
    method: 'GET',
    pattern: /^\/admin\/stats\/trend$/,
    handler: ({ query }) => {
      const days = Number(query.days || 30)
      return Array.from({ length: days }, (_, i) => {
        const d = new Date()
        d.setDate(d.getDate() - (days - 1 - i))
        return {
          date: d.toISOString().slice(0, 10),
          activeUsers: 40 + Math.round(Math.random() * 30),
          published: 5 + Math.round(Math.random() * 12),
          claimed: 4 + Math.round(Math.random() * 10),
        }
      })
    },
  },
  {
    method: 'GET',
    pattern: /^\/admin\/users$/,
    handler: ({ query }) => {
      let list = users.map((u) => ({
        ...publicUser(u),
        reportedCount: u.reportedCount,
        publishedCount: tasks.filter((t) => t.publisherId === u.id).length,
        claimedCount: tasks.filter((t) => t.takerId === u.id).length,
      }))
      if (query.keyword) {
        const kw = String(query.keyword).toLowerCase()
        list = list.filter(
          (u: any) => u.username.toLowerCase().includes(kw) || u.nickname.includes(query.keyword),
        )
      }
      if (query.status) list = list.filter((u: any) => u.status === query.status)
      return { list, total: list.length, page: 1, size: 20 }
    },
  },
  {
    method: 'PUT',
    pattern: /^\/admin\/users\/(\d+)\/ban$/,
    handler: ({ m, body }) => {
      if (!body.reason) throw fail('请填写封禁原因')
      const u = findUser(Number(m[1]))
      if (u) u.status = 'BANNED'
    },
  },
  {
    method: 'PUT',
    pattern: /^\/admin\/users\/(\d+)\/unban$/,
    handler: ({ m }) => {
      const u = findUser(Number(m[1]))
      if (u) u.status = 'NORMAL'
    },
  },
  {
    method: 'PUT',
    pattern: /^\/admin\/users\/(\d+)\/student-cert$/,
    handler: ({ m, body }) => {
      const u = findUser(Number(m[1]))
      if (u) u.authStatus = body.pass ? 'APPROVED' : 'REJECTED'
    },
  },
  {
    method: 'DELETE',
    pattern: /^\/admin\/users\/(\d+)$/,
    handler: ({ m }) => {
      const idx = users.findIndex((u) => u.id === Number(m[1]))
      if (idx >= 0) users.splice(idx, 1)
    },
  },
  {
    method: 'GET',
    pattern: /^\/admin\/audits$/,
    handler: ({ query }) => {
      let list = [...audits]
      if (query.status) list = list.filter((a) => a.status === query.status)
      return { list: clone(list), total: list.length, page: 1, size: 20 }
    },
  },
  {
    method: 'POST',
    pattern: /^\/admin\/audits\/(\d+)\/approve$/,
    handler: ({ m }) => {
      const a = audits.find((x) => x.id === Number(m[1]))
      if (a) a.status = 'APPROVED'
    },
  },
  {
    method: 'POST',
    pattern: /^\/admin\/audits\/(\d+)\/reject$/,
    handler: ({ m, body }) => {
      if (!body.reason) throw fail('请填写驳回原因')
      const a = audits.find((x) => x.id === Number(m[1]))
      if (a) a.status = 'REJECTED'
    },
  },
  {
    method: 'GET',
    pattern: /^\/admin\/reports$/,
    handler: ({ query }) => {
      let list = [...reports]
      if (query.status) list = list.filter((r) => r.status === query.status)
      return { list: clone(list), total: list.length, page: 1, size: 20 }
    },
  },
  {
    method: 'POST',
    pattern: /^\/admin\/reports\/(\d+)\/handle$/,
    handler: ({ m, body }) => {
      const r = reports.find((x) => x.id === Number(m[1]))
      if (r) {
        r.status = body.pass ? 'RESOLVED' : 'REJECTED'
        r.result = body.result
      }
    },
  },
  {
    method: 'GET',
    pattern: /^\/admin\/disputes$/,
    handler: ({ query }) => {
      let list = [...disputes]
      if (query.status) list = list.filter((d) => d.status === query.status)
      return { list: clone(list), total: list.length, page: 1, size: 20 }
    },
  },
  {
    method: 'POST',
    pattern: /^\/admin\/disputes\/(\d+)\/judge$/,
    handler: ({ m, body }) => {
      if (!body.remark || String(body.remark).length < 20) throw fail('裁决理由至少 20 字')
      const d = disputes.find((x) => x.id === Number(m[1]))
      if (d) {
        d.status = 'JUDGED'
        d.result = body.result
        d.remark = body.remark
      }
    },
  },
  {
    method: 'GET',
    pattern: /^\/admin\/tasks$/,
    handler: ({ query }) => {
      let list = tasks.map(decorateTask)
      if (query.status) list = list.filter((t: any) => t.status === query.status)
      if (query.keyword) {
        const kw = String(query.keyword).toLowerCase()
        list = list.filter((t: any) => t.title.toLowerCase().includes(kw))
      }
      return { list, total: list.length, page: 1, size: 20 }
    },
  },
  {
    method: 'POST',
    pattern: /^\/admin\/tasks\/(\d+)\/offline$/,
    handler: ({ m, body }) => {
      if (!body.reason) throw fail('请填写下架原因')
      const t = tasks.find((x) => x.id === Number(m[1]))
      if (t) t.status = 'CANCELLED'
    },
  },
  {
    method: 'POST',
    pattern: /^\/admin\/coins\/issue$/,
    handler: ({ body }) => {
      if (!body.amount || body.amount <= 0) throw fail('发放数量必须大于 0')
      if (body.amount > systemConfig.grantLimit) throw fail(`单次发放不得超过 ${systemConfig.grantLimit} WB`)
    },
  },
  {
    method: 'POST',
    pattern: /^\/admin\/coins\/issue-batch$/,
    handler: () => ({ batchId: `batch-${Date.now()}` }),
  },
  {
    method: 'POST',
    pattern: /^\/admin\/coins\/reverse$/,
    handler: ({ body }) => {
      if (!body.reason) throw fail('请填写冲正原因')
    },
  },
  {
    method: 'GET',
    pattern: /^\/admin\/coins\/audit$/,
    handler: () => [],
  },
  {
    method: 'GET',
    pattern: /^\/admin\/config$/,
    handler: () => clone(systemConfig),
  },
  {
    method: 'PUT',
    pattern: /^\/admin\/config$/,
    handler: ({ body }) => {
      Object.assign(systemConfig, body)
    },
  },
  {
    method: 'GET',
    pattern: /^\/admin\/logs$/,
    handler: () => ({ list: clone(operationLogs), total: operationLogs.length, page: 1, size: 20 }),
  },
]

/* ============================================================
 * 适配器实现
 * ============================================================ */

/** 解析 URL 上的查询串 */
function parseQuery(url: string): Record<string, string> {
  const idx = url.indexOf('?')
  if (idx < 0) return {}
  const search = url.slice(idx + 1)
  const out: Record<string, string> = {}
  new URLSearchParams(search).forEach((v, k) => (out[k] = v))
  return out
}

export const mockAdapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
  await delay()

  const method = (config.method ?? 'get').toUpperCase()
  const rawUrl = config.url ?? '/'
  const path = rawUrl.split('?')[0] ?? '/'
  const query = { ...parseQuery(rawUrl), ...(config.params ?? {}) }
  const body =
    typeof config.data === 'string'
      ? safeParse(config.data)
      : ((config.data as Record<string, any>) ?? {})

  const handle = () => {
    for (const route of routes) {
      if (route.method !== method) continue
      const m = path.match(route.pattern)
      if (!m) continue
      const user = currentUserId ? (findUser(currentUserId) ?? null) : null
      return route.handler({ m, method, query: query as Record<string, string>, body, user })
    }
    throw new MockFail(`Mock 未实现的接口：${method} ${path}`, 40400)
  }

  let result: ApiResult
  try {
    result = { code: 0, message: 'ok', data: handle() }
  } catch (e) {
    if (e instanceof MockFail) {
      result = { code: e.code, message: e.message, data: null }
    } else {
      result = { code: 50000, message: (e as Error).message || 'Mock 内部错误', data: null }
    }
  }

  const response: AxiosResponse = {
    data: result,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
  return response
}

function safeParse(text: string): Record<string, any> {
  try {
    return JSON.parse(text)
  } catch {
    return {}
  }
}
