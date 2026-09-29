/** 阿婆干饭社 管理端类型定义（T2：店铺信息/分类/菜品） */

/** 店铺信息 */
export interface BizShopInfo {
  shopId?: number
  shopName?: string
  address?: string
  longitude?: number
  latitude?: number
  businessHours?: string
  /** 1营业中 0已打烊 */
  businessStatus?: number
  phone?: string
  notice?: string
  /** 外卖配送费 */
  deliveryFee?: number
  /** 外卖起送价 */
  minDeliveryAmount?: number
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 菜品分类 */
export interface BizCategory {
  id?: number
  name?: string
  sort?: number
  /** 0正常 1停用 */
  status?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** 菜品规格 */
export interface BizGoodsSpec {
  id?: number
  goodsId?: number
  name?: string
  /** 规格差价 */
  priceDelta?: number
  sort?: number
  /** 规格级售罄标记（0未售罄 1已售罄） */
  soldOut?: string
  /** 自动售罄标记（T12：0否 1是，库存耗尽自动置位） */
  autoSoldOut?: string
  /** 规格级每日限量值（T12：留空则回落菜品级） */
  dailyLimitQty?: number
}

/** 菜品口味组 */
export interface BizGoodsFlavor {
  id?: number
  goodsId?: number
  name?: string
  /** JSON数组字符串，如 ["微辣","中辣"] */
  options?: string
  /** 0单选 1多选 */
  selectType?: string
  sort?: number
}

/** 菜品（含规格/口味级联） */
export interface BizGoods {
  id?: number
  categoryId?: number
  name?: string
  /** 只存相对路径 */
  image?: string
  price?: number
  description?: string
  /** 1在售 0下架 */
  status?: string
  /** 0否 1是（菜品级售罄） */
  soldOut?: string
  /** 自动售罄标记（T12：0否 1是，库存耗尽自动置位，不覆盖手动售罄） */
  autoSoldOut?: string
  /** 是否启用每日限量（T12：0否 1是） */
  dailyLimitEnabled?: string
  /** 每日限量值（T12 菜品级模板；规格级以规格行的值为准） */
  dailyLimitQty?: number
  sales?: number
  sort?: number
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
  specs?: BizGoodsSpec[]
  flavors?: BizGoodsFlavor[]
}

/** 菜品查询参数 */
export interface GoodsQueryParams {
  pageNum: number
  pageSize: number
  name?: string
  categoryId?: number
  status?: string
  soldOut?: string
}

/** 管理端订单（列表行/详情均基于 biz_order 字段） */
export interface MerchantOrder {
  id?: number
  orderNo?: string
  memberId?: number
  /** 1堂食 2外卖 */
  deliveryType?: number
  tableNo?: string
  contactName?: string
  contactPhone?: string
  /** 地址快照 JSON 字符串 */
  addressSnapshot?: string
  goodsAmount?: number
  deliveryFee?: number
  payAmount?: number
  /** 0待支付 1待接单 2已接单制作中 3已出餐待取餐 4配送中 5已完成 6已取消 */
  status?: number
  /** 0无退款 1退款审核中 2已退款 */
  refundStatus?: number
  remark?: string
  payStatus?: number
  payTime?: string
  /** T18 配送环节时间 */
  readyTime?: string
  pickedUpTime?: string
  deliveredTime?: string
  timeoutCloseTime?: string
  createTime?: string
  updateTime?: string
}

/** 订单查询参数 */
export interface OrderQueryParams {
  pageNum: number
  pageSize: number
  orderNo?: string
  status?: number
  deliveryType?: number
}

/** AI 知识库文档（二期 RAG） */
export interface AiKnowledge {
  id?: number
  fileName?: string
  filePath?: string
  fileType?: string
  fileSize?: number
  /** 切片数量（解析成功后写入） */
  chunkCount?: number
  /** 0待解析 1解析中 2解析成功 3解析失败 */
  parseStatus?: number
  failReason?: string
  createBy?: string
  createTime?: string
  updateTime?: string
}

/** 知识库查询参数 */
export interface KnowledgeQueryParams {
  pageNum: number
  pageSize: number
  fileName?: string
  parseStatus?: number
}
