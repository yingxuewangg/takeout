#!/bin/bash
# ============================================================
# 阿婆干饭社 —— 中文编码自检脚本
# 用途：扫描数据库中所有文本字段，找出两类"坏中文"：
#   ① U+FFFD 替换字符（hex EFBFBD）——GBK 字节被按 UTF-8 解码；
#   ② 连续问号（hex 3F3F3F）——命令行参数编码转换失败，中文被替换为 '?'。
#
# 背景（重要）：
#   本项目链路本身编码正常（浏览器/小程序写入的中文读写一致，已实测）。
#   两类坏数据都来自"在 Git Bash 里用 curl -d 内联中文"：
#     · 中文被转成 GBK 字节发出 → 服务端按 UTF-8 解码 → U+FFFD（EFBFBD）；
#     · 参数经 MSYS → 原生 Windows 程序（curl.exe/node.exe）转码失败 → 字面 '?'（3F）。
#   规范：测试时含中文的请求体必须写入文件后用 --data-binary @file 发送（或直接用
#   node/Postman/页面操作）；JSON 交 node 处理时走 stdin，不要经命令行参数传递。
#
# 用法：在 ruoyi-takeout-shop 目录执行  bash scripts/check-encoding.sh
# ============================================================
set -u

echo "=== 中文编码自检（U+FFFD 替换字符 + 字面问号污染）==="
echo

FAIL=0
check() { # check "<表.字段>" "<表>" "<字段>"
  local label="$1" table="$2" col="$3"
  local n nq
  n=$(docker exec takeout-mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -e \
      "select count(*) from takeout.${table} where hex(${col}) like '%EFBFBD%';" 2>/dev/null | tr -d '\r')
  n=${n:-0}
  nq=$(docker exec takeout-mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -e \
      "select count(*) from takeout.${table} where hex(${col}) like '%3F3F3F%';" 2>/dev/null | tr -d '\r')
  nq=${nq:-0}
  if [ "$n" = "0" ] && [ "$nq" = "0" ]; then
    echo "OK    ${label}"
  else
    [ "$n" != "0" ] && echo "乱码  ${label} —— 发现 ${n} 行 U+FFFD 替换字符（请检查并修正）"
    [ "$nq" != "0" ] && echo "问号  ${label} —— 发现 ${nq} 行连续问号（3F，疑似内联中文被破坏）"
    docker exec takeout-mysql mysql -uroot -proot --default-character-set=utf8mb4 -e \
      "select id, left(${col},40) as value_preview from takeout.${table}
        where hex(${col}) like '%EFBFBD%' or hex(${col}) like '%3F3F3F%' limit 5;" 2>/dev/null | tail -n +2
    FAIL=$((FAIL+1))
  fi
}

# 一期业务表
check "店铺信息.名称/地址/公告"      biz_shop_info     shop_name
check "店铺信息.地址"                biz_shop_info     address
check "店铺信息.公告"                biz_shop_info     notice
check "菜品.名称"                    biz_goods         name
check "菜品.描述"                    biz_goods         description
check "分类.名称"                    biz_category      name
check "规格.名称"                    biz_goods_spec    name
check "口味.名称"                    biz_goods_flavor  name
check "口味.选项"                    biz_goods_flavor  options
check "订单.联系人"                  biz_order         contact_name
check "订单.地址快照"                biz_order         address_snapshot
check "订单.备注"                    biz_order         remark
check "订单明细.菜品名"              biz_order_item    goods_name
check "订单明细.规格口味快照"        biz_order_item    spec_flavor_json
check "退款单.退款原因"              biz_order_refund  reason
check "退款单.驳回理由"              biz_order_refund  reject_reason
check "地址簿.联系人"                biz_address       contact_name
check "地址簿.详细地址"              biz_address       detail
check "地址簿.省市区"                biz_address       province
check "留言.内容"                    biz_goods_comment content
check "留言.商家回复"                biz_goods_comment reply
check "用户.昵称"                    biz_member        nickname
# 二期 AI 表
check "知识库.文件名"                biz_ai_knowledge     file_name
check "AI会话.标题"                  biz_ai_chat_session  title
check "AI消息.内容"                  biz_ai_chat_message  content
check "AI消息.知识引用"              biz_ai_chat_message  references_json

echo
if [ "$FAIL" -eq 0 ]; then
  echo "结论：未发现乱码，编码链路正常 ✅"
else
  echo "结论：发现 ${FAIL} 处乱码字段 ⚠️"
  echo "排查建议："
  echo "  1) 若乱码集中在测试数据上 → 多为测试时用 curl 内联中文导致（见脚本头部说明），清理测试数据即可；"
  echo "  2) 若新写入的中文也乱码 → 用页面（浏览器/小程序）操作验证；若页面正常而 curl 乱码，则是测试方式问题；"
  echo "  3) 若页面也乱码 → 检查数据库/表的字符集：show create table <表名>（应为 utf8mb4）。"
fi
