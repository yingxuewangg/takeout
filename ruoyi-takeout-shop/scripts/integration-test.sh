#!/bin/bash
# ============================================================
# 阿婆干饭社 —— 整体联调回归脚本（T9 + T11 规格级售罄）
# 用法：在 ruoyi-takeout-shop 目录执行  bash scripts/integration-test.sh
# 前置：docker compose 环境已启动；后端已启动且带 --takeout.wx.mock-enabled=true
# 说明：全场景 API 断言，每步输出 PASS/FAIL，结尾统计。
#       中文请求体一律「写入文件 + --data-binary @file」发送：Git Bash 把命令行参数传给
#       原生 Windows 程序（curl.exe/node.exe）时会按 GBK 转码，curl -d "中文" 会破坏中文；
#       脚本内中文经重定向写文件、经管道（stdin）传给 node 则不受影响。
#       并发段响应经 /tmp 文件收集（MSYS 程序间安全）。
# ============================================================
BASE="http://localhost:4121"
PASS=0
FAIL=0
TOTAL=0

expect() {
  local name="$1" actual="$2" want="$3"
  TOTAL=$((TOTAL+1))
  if echo "$actual" | grep -q "$want"; then
    PASS=$((PASS+1))
    echo "PASS  $name"
  else
    FAIL=$((FAIL+1))
    echo "FAIL  $name  [实际: $(echo "$actual" | head -c 100)]"
  fi
}

jget() { # $1=JSON（经 stdin 传递，避免命令行参数编码转换破坏中文），$2=字段路径
  printf '%s' "$1" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{try{const d=JSON.parse(s);console.log(eval('d.'+process.argv[1])??'')}catch(e){console.log('')}})" "$2" 2>/dev/null
}

sql() {
  docker exec takeout-mysql mysql -uroot -proot -N -e "$1" 2>/dev/null | tr -d '\r'
}

jsonlen() { # echo "$json" | jsonlen "data" -> 数组/对象长度
  node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{try{const v=eval('JSON.parse(s).'+process.argv[1]);console.log(v.length??0)}catch(e){console.log(0)}})" 2>/dev/null
}

echo "==================== 0. 环境就绪 ===================="
expect "后端存活" "$(curl -s -o /dev/null -w '%{http_code}' $BASE/captchaImage)" "200"
expect "游客店铺信息" "$(curl -s $BASE/api/shop/info)" '"businessStatus":1'

echo "==================== 1. 登录与游客浏览 ===================="
LOGIN_RESP=$(curl -s -X POST $BASE/api/login -H "Content-Type: application/json" -d '{"code":"mock-openid-itest"}')
TOKEN=$(jget "$LOGIN_RESP" "data.token")
expect "mock 登录获得 token" "$TOKEN" "."
AUTH="Authorization: Bearer $TOKEN"
MEMBER_ID=$(jget "$LOGIN_RESP" "data.memberId")
MENU_RESP=$(curl -s "$BASE/api/menu/list")
expect "游客菜单列表（缓存回填）" "$MENU_RESP" '"categoryId"'
expect "菜单图片 URL 已拼接前缀" "$(jget "$MENU_RESP" "data[0].goodsList[0].image")" "http://localhost:4121"
expect "未登录访问购物车 401" "$(curl -s $BASE/api/cart/list)" '"code":401'

echo "==================== 2. 购物车（合并） ===================="
curl -s -X DELETE $BASE/api/cart/clear -H "$AUTH" > /dev/null
# 规格 ID 动态查询（管理端编辑走"先删后插"，规格 ID 会变化，不能硬编码）
SPEC_PRICE3=$(sql "select id from takeout.biz_goods_spec where goods_id=1 and price_delta=3.00 limit 1")
ADD_SIMPLE='{"goodsId":1,"quantity":2}'
# 含中文的请求体写入文件后用 --data-binary 发送（见脚本头部说明）
cat > /tmp/it_add_spec.json <<EOF
{"goodsId":1,"specId":$SPEC_PRICE3,"flavorJson":"[{\"name\":\"辣度\",\"values\":[\"微辣\"]}]","quantity":1}
EOF
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "$ADD_SIMPLE" > /dev/null
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" --data-binary @/tmp/it_add_spec.json > /dev/null
CART_RESP=$(curl -s $BASE/api/cart/list -H "$AUTH")
expect "购物车两行（同组合合并）" "$(echo "$CART_RESP" | grep -o '"goodsId"' | wc -l)" "^2$"
expect "购物车明细含快照单价（基础价+规格差价）" "$CART_RESP" '"price":21.00'

echo "==================== 3. 下单与防重锁 ===================="
ORDER_BODY='{"deliveryType":1,"tableNo":"66","contactPhone":"13800000001"}'
# 防重锁并发测试（此时该用户无残留锁）：两个请求恰一次成功，成功单清空购物车
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "$ADD_SIMPLE" > /dev/null
CNT_BEFORE=$(sql "select count(*) from takeout.biz_order where member_id=$MEMBER_ID")
curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY" > /tmp/it_r1.json &
curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY" > /tmp/it_r2.json &
wait
CNT_AFTER=$(sql "select count(*) from takeout.biz_order where member_id=$MEMBER_ID")
expect "并发两次下单仅成功一次（防重锁，订单数+1）" "$((CNT_AFTER-CNT_BEFORE))" "^1$"
CART_LEN=$(curl -s $BASE/api/cart/list -H "$AUTH" | jsonlen "data")
expect "成功单清空购物车" "$CART_LEN" "^0$"
# 成功单持有 5 秒防重锁，等锁过期后再走正常下单链路
sleep 5
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "$ADD_SIMPLE" > /dev/null
O1_RESP=$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY")
OID=$(jget "$O1_RESP" "data.orderId")
expect "下单成功" "$O1_RESP" '"code":200'

echo "==================== 4. 模拟支付 ===================="
SALES_BEFORE=$(sql "select sales from takeout.biz_goods where id=1")
expect "支付金额篡改拒绝（先于支付）" "$(curl -s -X POST $BASE/api/pay/mock -H "$AUTH" -H "Content-Type: application/json" -d "{\"orderId\":$OID,\"amount\":0.01}")" "不一致"
PAY_RESP=$(curl -s -X POST $BASE/api/pay/mock -H "$AUTH" -H "Content-Type: application/json" -d "{\"orderId\":$OID}")
expect "支付成功（只传 orderId）" "$PAY_RESP" '"code":200'
SALES_AFTER=$(sql "select sales from takeout.biz_goods where id=1")
expect "销量同步累加（+2）" "$((SALES_AFTER-SALES_BEFORE))" "^2$"
expect "重复支付幂等拦截" "$(curl -s -X POST $BASE/api/pay/mock -H "$AUTH" -H "Content-Type: application/json" -d "{\"orderId\":$OID}")" "请勿重复支付"

echo "==================== 5. 管理端流转（堂食链 1→2→3→5） ===================="
docker exec takeout-redis redis-cli del "sys_config:sys.account.captchaEnabled" > /dev/null
ALOGIN_RESP=$(curl -s -X POST $BASE/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}')
ATOKEN=$(jget "$ALOGIN_RESP" "token")
A="Authorization: Bearer $ATOKEN"
curl -s -X PUT $BASE/merchant/order/accept/$OID -H "$A" > /dev/null
curl -s -X PUT $BASE/merchant/order/ready/$OID -H "$A" > /dev/null
expect "堂食确认完成（3→5）" "$(curl -s -X PUT $BASE/merchant/order/complete/$OID -H "$A")" '"code":200'
expect "已完成订单重放接单被精确提示" "$(curl -s -X PUT $BASE/merchant/order/accept/$OID -H "$A")" "无法接单"

sleep 5  # 等上一次下单的 5 秒防重锁过期
echo "==================== 6. 外卖链（配送费快照/deliver 仅外卖） ===================="
# 为 itest 用户创建专属收货地址（地址归属校验：不能用其他用户的地址；含中文 → 文件方式）
cat > /tmp/it_addr.json <<'EOF'
{"contactName":"联调收货","contactPhone":"13800000001","province":"广东省","city":"广州市","district":"天河区","detail":"联调路 1 号"}
EOF
ADDR_RESP=$(curl -s -X POST $BASE/api/address -H "$AUTH" -H "Content-Type: application/json" --data-binary @/tmp/it_addr.json)
ADDR_ID=$(sql "select id from takeout.biz_address where member_id=$MEMBER_ID order by id desc limit 1")
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "$ADD_SIMPLE" > /dev/null
OB_RESP=$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "{\"deliveryType\":2,\"addressId\":$ADDR_ID}")
BID=$(jget "$OB_RESP" "data.orderId")
curl -s -X POST $BASE/api/pay/mock -H "$AUTH" -H "Content-Type: application/json" -d "{\"orderId\":$BID}" > /dev/null
for step in accept ready deliver complete; do
  curl -s -X PUT $BASE/merchant/order/$step/$BID -H "$A" > /dev/null
done
expect "外卖全链完成（status=5）" "$(sql "select status from takeout.biz_order where id=$BID")" "^5$"

sleep 5
echo "==================== 7. 退款链 ===================="
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "$ADD_SIMPLE" > /dev/null
OC_RESP=$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d '{"deliveryType":1,"tableNo":"77","contactPhone":"13800000002"}')
CID=$(jget "$OC_RESP" "data.orderId")
curl -s -X POST $BASE/api/pay/mock -H "$AUTH" -H "Content-Type: application/json" -d "{\"orderId\":$CID}" > /dev/null
cat > /tmp/it_refund.json <<EOF
{"orderId":$CID,"reason":"联调测试退款"}
EOF
curl -s -X POST $BASE/api/refund/apply -H "$AUTH" -H "Content-Type: application/json" --data-binary @/tmp/it_refund.json > /dev/null
expect "退款单生成且订单冻结（流转被拒）" "$(curl -s -X PUT $BASE/merchant/order/accept/$CID -H "$A")" "退款审核中"
RFID=$(sql "select id from takeout.biz_order_refund where order_no=(select order_no from takeout.biz_order where id=$CID) and audit_status=0")
curl -s -X PUT $BASE/merchant/refund/approve/$RFID -H "$A" > /dev/null
expect "退款通过订单置终态（refund_status=2）" "$(sql "select refund_status from takeout.biz_order where id=$CID")" "^2$"
expect "终态后流转被拒" "$(curl -s -X PUT $BASE/merchant/order/accept/$CID -H "$A")" "已退款"
expect "审核重放幂等" "$(curl -s -X PUT $BASE/merchant/refund/approve/$RFID -H "$A")" "已处理"

echo "==================== 8. 留言资格（仅买过） ===================="
# 未购用户（mock-openid-nobuy，无任何订单）发表留言应被拒
NOBUY_RESP=$(curl -s -X POST $BASE/api/login -H "Content-Type: application/json" -d '{"code":"mock-openid-nobuy"}')
NOBUY_TOKEN=$(jget "$NOBUY_RESP" "data.token")
cat > /tmp/it_comment_nobuy.json <<'EOF'
{"goodsId":1,"content":"联调测试留言","score":5}
EOF
expect "未购用户留言被拒" "$(curl -s -X POST $BASE/api/comment -H "Authorization: Bearer $NOBUY_TOKEN" -H "Content-Type: application/json" --data-binary @/tmp/it_comment_nobuy.json)" "购买后才能留言"
# 已购用户（mock-openid-1001，取其本人一笔合格订单关联发表）
L2_RESP=$(curl -s -X POST $BASE/api/login -H "Content-Type: application/json" -d '{"code":"mock-openid-1001"}')
T2=$(jget "$L2_RESP" "data.token")
BOUGHT_OID=$(sql "select o.id from takeout.biz_order o join takeout.biz_order_item i on i.order_id=o.id where o.member_id=(select id from takeout.biz_member where openid='mock-openid-1001') and o.status between 1 and 5 and o.refund_status!=2 and i.goods_id=1 limit 1")
cat > /tmp/it_comment_ok.json <<EOF
{"goodsId":1,"orderId":$BOUGHT_OID,"content":"联调测试留言（可删除）","score":5}
EOF
curl -s -X POST $BASE/api/comment -H "Authorization: Bearer $T2" -H "Content-Type: application/json" --data-binary @/tmp/it_comment_ok.json > /dev/null
echo '{"goodsId":1,"orderId":'$BOUGHT_OID',"content":"包含敏感词刷单的留言","score":5}' > /tmp/it_sens.json
expect "敏感词拦截（已购用户发敏感词内容）" "$(curl -s -X POST $BASE/api/comment -H "Authorization: Bearer $T2" -H "Content-Type: application/json" --data-binary @/tmp/it_sens.json)" "敏感词"
expect "留言列表可见已购留言（hasOrder=true）" "$(curl -s "$BASE/api/comment/list/1")" '"hasOrder":true'

sleep 5
echo "==================== 9. 打烊拦截 ===================="
docker exec takeout-mysql mysql -uroot -proot -e "update takeout.biz_shop_info set business_status=0 where shop_id=1" 2>/dev/null
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "$ADD_SIMPLE" > /dev/null
expect "打烊下单拦截" "$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY")" "打烊"
docker exec takeout-mysql mysql -uroot -proot -e "update takeout.biz_shop_info set business_status=1 where shop_id=1" 2>/dev/null

echo "==================== 10. 缓存一致性（只删不更新） ===================="
docker exec takeout-redis redis-cli set "takeout:goods:list" "stale" > /dev/null
docker exec takeout-mysql mysql -uroot -proot -e "update takeout.biz_goods set sold_out='1' where id=1" 2>/dev/null
curl -s -X PUT "$BASE/merchant/goods/soldOut?ids=1&soldOut=1" -H "$A" > /dev/null
expect "管理端写操作后缓存 key 被删除" "$(docker exec takeout-redis redis-cli exists "takeout:goods:list" 2>/dev/null)" "^0$"
expect "脏缓存容错（损坏缓存不 500，回填新值）" "$(curl -s "$BASE/api/menu/list")" '"categoryId"'
docker exec takeout-mysql mysql -uroot -proot -e "update takeout.biz_goods set sold_out='0' where id=1" 2>/dev/null
curl -s -X PUT "$BASE/merchant/goods/soldOut?ids=1&soldOut=0" -H "$A" > /dev/null

echo "==================== 11. 规格级售罄（T11） ===================="
# 前置清理：关闭每日限量并清空库存，保证 T11 断言只受"手动规格售罄"影响（T12 段会自行开启限量）
sql "update takeout.biz_goods set daily_limit_enabled=0, auto_sold_out=0, sold_out=0 where id=1" > /dev/null
sql "update takeout.biz_goods_spec set auto_sold_out=0, sold_out=0 where goods_id=1" > /dev/null
sql "delete from takeout.biz_goods_stock_daily where goods_id=1" > /dev/null
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null
# 真实路径：经管理端编辑接口（PUT /merchant/goods）把「大份」标记规格级售罄。
# 菜品 JSON 含中文：响应经 stdin 传给 node 处理，处理结果写文件后用 --data-binary 上报（避免命令行编码转换）
G_RESP=$(curl -s $BASE/merchant/goods/1 -H "$A")
printf '%s' "$G_RESP" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;['createBy','createTime','updateBy','updateTime','sales'].forEach(k=>delete d[k]);d.specs.forEach(sp=>{delete sp.goodsId;sp.soldOut=(Number(sp.priceDelta)===3)?'1':'0'});process.stdout.write(JSON.stringify(d))})" > /tmp/it_goods_t11.json
curl -s -X PUT $BASE/merchant/goods -H "$A" -H "Content-Type: application/json" --data-binary @/tmp/it_goods_t11.json > /dev/null
expect "规格售罄保存后缓存 key 被删除（只删不更新）" "$(docker exec takeout-redis redis-cli exists "takeout:goods:list" 2>/dev/null)" "^0$"
SPEC_D=$(sql "select id from takeout.biz_goods_spec where goods_id=1 and price_delta=3.00 limit 1")
SPEC_X=$(sql "select id from takeout.biz_goods_spec where goods_id=1 and price_delta=-2.00 limit 1")
MENU_T11=$(curl -s "$BASE/api/menu/list")
expect "部分规格售罄：列表不置灰（soldOut=0）" "$(echo "$MENU_T11" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s);const g=d.data.flatMap(c=>c.goodsList).find(g=>g.id===1);console.log(g.soldOut)})")" "^0$"
DETAIL_T11=$(curl -s "$BASE/api/goods/detail/1")
expect "详情规格下发售罄标记（仅大份）" "$(echo "$DETAIL_T11" | grep -o '"soldOut":"1"' | wc -l)" "^1$"
expect "售罄规格加购被拒" "$(curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "{\"goodsId\":1,\"specId\":$SPEC_D,\"quantity\":1}")" "已售罄"
expect "未售罄规格加购正常" "$(curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "{\"goodsId\":1,\"specId\":$SPEC_X,\"quantity\":1}")" '"code":200'
# 全部规格售罄（同上，走真实管理端编辑接口）→ 列表展示为售罄；购物车行失效、结算与下单拦截
G_RESP2=$(curl -s $BASE/merchant/goods/1 -H "$A")
printf '%s' "$G_RESP2" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;['createBy','createTime','updateBy','updateTime','sales'].forEach(k=>delete d[k]);d.specs.forEach(sp=>{delete sp.goodsId;sp.soldOut='1'});process.stdout.write(JSON.stringify(d))})" > /tmp/it_goods_allsold.json
curl -s -X PUT $BASE/merchant/goods -H "$A" -H "Content-Type: application/json" --data-binary @/tmp/it_goods_allsold.json > /dev/null
expect "全部规格售罄：列表展示为售罄（soldOut=1）" "$(curl -s "$BASE/api/menu/list" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s);const g=d.data.flatMap(c=>c.goodsList).find(g=>g.id===1);console.log(g.soldOut)})")" "^1$"
CART_T11=$(curl -s $BASE/api/cart/list -H "$AUTH")
expect "购物车规格售罄行标记 soldout" "$CART_T11" '"status":"soldout"'
expect "结算拦截（canSubmit=false）" "$(curl -s "$BASE/api/cart/checkout?deliveryType=1&tableNo=66" -H "$AUTH")" '"canSubmit":false'
expect "下单二次校验拦截" "$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY")" "已售罄"
# 恢复：取消全部规格售罄（走真实管理端编辑接口，验证缓存删除后列表恢复）
G_RESP3=$(curl -s $BASE/merchant/goods/1 -H "$A")
printf '%s' "$G_RESP3" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;['createBy','createTime','updateBy','updateTime','sales'].forEach(k=>delete d[k]);d.specs.forEach(sp=>{delete sp.goodsId;sp.soldOut='0'});process.stdout.write(JSON.stringify(d))})" > /tmp/it_goods_reset.json
curl -s -X PUT $BASE/merchant/goods -H "$A" -H "Content-Type: application/json" --data-binary @/tmp/it_goods_reset.json > /dev/null
expect "取消规格售罄后列表恢复（soldOut=0）" "$(curl -s "$BASE/api/menu/list" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s);const g=d.data.flatMap(c=>c.goodsList).find(g=>g.id===1);console.log(g.soldOut)})")" "^0$"
curl -s -X DELETE $BASE/api/cart/clear -H "$AUTH" > /dev/null

echo "==================== 12. 每日限量库存 + 自动售罄（T12） ===================="
# 准备：启用每日限量（规格级大份=2、小份=3），并清空当天库存行
SPEC_D12=$(sql "select id from takeout.biz_goods_spec where goods_id=1 and price_delta=3.00 limit 1")
SPEC_X12=$(sql "select id from takeout.biz_goods_spec where goods_id=1 and price_delta=-2.00 limit 1")
sql "update takeout.biz_goods set daily_limit_enabled=1, daily_limit_qty=10, auto_sold_out=0, sold_out=0 where id=1" > /dev/null
sql "update takeout.biz_goods_spec set daily_limit_qty=2, auto_sold_out=0, sold_out=0 where id=$SPEC_D12" > /dev/null
sql "update takeout.biz_goods_spec set daily_limit_qty=3, auto_sold_out=0, sold_out=0 where id=$SPEC_X12" > /dev/null
sql "delete from takeout.biz_goods_stock_daily where goods_id=1" > /dev/null
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null

# 1) 惰性初始化：查询库存接口应自动建当天库存行
STOCK12=$(curl -s "$BASE/api/goods/stock/1")
expect "库存接口返回启用标记与规格库存" "$STOCK12" '"dailyLimitEnabled":true'
expect "规格库存惰性初始化（大份限量2）" "$STOCK12" '"limitQty":2'

# 2) 下单扣减 + 自动售罄（大份限量2，下单2份应扣满并自动售罄）
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "{\"goodsId\":1,\"specId\":$SPEC_D12,\"quantity\":2}" > /dev/null
O12=$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY")
OID12=$(jget "$O12" "data.orderId")
expect "下单扣减成功" "$O12" '"code":200'
expect "扣减后 sold_qty=2" "$(sql "select sold_qty from takeout.biz_goods_stock_daily where goods_id=1 and spec_id=$SPEC_D12")" "^2$"
expect "库存耗尽自动售罄（auto_sold_out=1，手动 sold_out 保持 0）" "$(sql "select concat(sold_out,'-',auto_sold_out) from takeout.biz_goods_spec where id=$SPEC_D12")" "^0-1$"
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null
expect "详情规格展示为售罄（有效售罄）" "$(curl -s "$BASE/api/goods/detail/1" | grep -o "\"soldOut\":\"1\"" | wc -l)" "^1$"

# 3) 库存不足拦截：满额后再下单应被拒（防超卖）
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "{\"goodsId\":1,\"specId\":$SPEC_X12,\"quantity\":3}" > /dev/null
sleep 5
O13=$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY")
OID13=$(jget "$O13" "data.orderId")
expect "小份下单3份成功（限量3）" "$O13" '"code":200'
expect "小份售罄后加购被拒（库存校验）" "$(curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "{\"goodsId\":1,\"specId\":$SPEC_X12,\"quantity\":1}")" "已售罄"

# 4) 取消订单释放库存（幂等）
sql "update takeout.biz_goods_stock_daily set sold_qty=3 where goods_id=1 and spec_id=$SPEC_X12" > /dev/null
expect "取消待支付订单" "$(curl -s -X PUT $BASE/api/order/cancel/$OID13 -H "$AUTH")" '"code":200'
expect "取消后库存释放（sold_qty 归零）" "$(sql "select sold_qty from takeout.biz_goods_stock_daily where goods_id=1 and spec_id=$SPEC_X12")" "^0$"
expect "释放后自动售罄取消（auto_sold_out=0）" "$(sql "select auto_sold_out from takeout.biz_goods_spec where id=$SPEC_X12")" "^0$"
expect "释放幂等（重复取消不再释放）" "$(curl -s -X PUT $BASE/api/order/cancel/$OID13 -H "$AUTH")" "已取消"
expect "幂等校验：sold_qty 仍为 0" "$(sql "select sold_qty from takeout.biz_goods_stock_daily where goods_id=1 and spec_id=$SPEC_X12")" "^0$"

# 5) 退款成功释放库存
curl -s -X POST $BASE/api/pay/mock -H "$AUTH" -H "Content-Type: application/json" -d "{\"orderId\":$OID12}" > /dev/null
cat > /tmp/it_refund_t12.json <<EOF
{"orderId":$OID12,"reason":"T12 联调退款"}
EOF
curl -s -X POST $BASE/api/refund/apply -H "$AUTH" -H "Content-Type: application/json" --data-binary @/tmp/it_refund_t12.json > /dev/null
RFID12=$(sql "select id from takeout.biz_order_refund where order_no=(select order_no from takeout.biz_order where id=$OID12) and audit_status=0")
curl -s -X PUT $BASE/merchant/refund/approve/$RFID12 -H "$A" > /dev/null
expect "退款成功后库存释放（大份 sold_qty 归零）" "$(sql "select sold_qty from takeout.biz_goods_stock_daily where goods_id=1 and spec_id=$SPEC_D12")" "^0$"
expect "退款释放后自动售罄取消" "$(sql "select auto_sold_out from takeout.biz_goods_spec where id=$SPEC_D12")" "^0$"

# 6) 超时关单兜底释放库存
sleep 5
curl -s -X POST $BASE/api/cart/add -H "$AUTH" -H "Content-Type: application/json" -d "{\"goodsId\":1,\"specId\":$SPEC_X12,\"quantity\":2}" > /dev/null
O14=$(curl -s -X POST $BASE/api/order/create -H "$AUTH" -H "Content-Type: application/json" -d "$ORDER_BODY")
OID14=$(jget "$O14" "data.orderId")
sql "update takeout.biz_order set timeout_close_time='2020-01-01 00:00:00' where id=$OID14" > /dev/null
curl -s -X PUT "$BASE/monitor/job/run" -H "$A" -H "Content-Type: application/json" -d '{"jobId":100,"jobGroup":"DEFAULT"}' > /dev/null
sleep 4
expect "超时关单后状态为已取消" "$(sql "select status from takeout.biz_order where id=$OID14")" "^6$"
expect "超时关单释放库存（sold_qty 归零）" "$(sql "select sold_qty from takeout.biz_goods_stock_daily where goods_id=1 and spec_id=$SPEC_X12")" "^0$"

# 7) 管理端：今日库存列表 + 手动重置补货
expect "管理端今日库存列表" "$(curl -s $BASE/merchant/stock/today -H "$A")" '"goodsName"'
curl -s -X PUT "$BASE/merchant/stock/reset?goodsId=1&specId=$SPEC_X12&limitQty=8" -H "$A" > /dev/null
expect "手动重置补货（限量改为8、已售归零）" "$(sql "select concat(limit_qty,'-',sold_qty) from takeout.biz_goods_stock_daily where goods_id=1 and spec_id=$SPEC_X12")" "^8-0$"

# 8) 手动售罄优先级：自动逻辑不得覆盖手动售罄
sql "update takeout.biz_goods_spec set sold_out='1' where id=$SPEC_X12" > /dev/null
curl -s -X PUT "$BASE/merchant/stock/reset?goodsId=1&specId=$SPEC_X12&limitQty=8" -H "$A" > /dev/null
expect "手动售罄不被自动逻辑覆盖（sold_out 仍为 1、auto 归 0）" "$(sql "select concat(sold_out,'-',auto_sold_out) from takeout.biz_goods_spec where id=$SPEC_X12")" "^1-0$"
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null
expect "详情展示手动售罄（remain 充足仍售罄）" "$(curl -s "$BASE/api/goods/detail/1" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;const x=d.specs.find(v=>v.id==$SPEC_X12);console.log(x.soldOut)})")" "^1$"

# 收尾：关闭限量、清理当天库存与测试数据，恢复初始态
sql "update takeout.biz_goods_spec set sold_out='0', auto_sold_out=0, daily_limit_qty=null where goods_id=1" > /dev/null
sql "update takeout.biz_goods set daily_limit_enabled=0, daily_limit_qty=0, auto_sold_out=0, sold_out=0 where id=1" > /dev/null
sql "delete from takeout.biz_goods_stock_daily where goods_id=1" > /dev/null
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$AUTH" > /dev/null

echo "==================== 13. 历史订单 / 再次购买（T13） ===================="
# 准备：新建一个独立用户，构造“含规格+口味”的已支付订单（主状态 1，可再次购买）
L13=$(curl -s -X POST $BASE/api/login -H "Content-Type: application/json" -d '{"code":"mock-openid-itest13"}')
T13=$(jget "$L13" "data.token")
A13="Authorization: Bearer $T13"
curl -s -X DELETE $BASE/api/cart/clear -H "$A13" > /dev/null
cat > /tmp/it_t13_add.json <<EOF
{"goodsId":1,"specId":$SPEC_D12,"flavorJson":"[{\"name\":\"辣度\",\"values\":[\"微辣\"]}]","quantity":2}
EOF
curl -s -X POST $BASE/api/cart/add -H "$A13" -H "Content-Type: application/json" --data-binary @/tmp/it_t13_add.json > /dev/null
O15=$(curl -s -X POST $BASE/api/order/create -H "$A13" -H "Content-Type: application/json" -d '{"deliveryType":1,"tableNo":"13"}')
OID15=$(jget "$O15" "data.orderId")
curl -s -X POST $BASE/api/pay/mock -H "$A13" -H "Content-Type: application/json" -d "{\"orderId\":$OID15}" > /dev/null

# 1) 展示条件：主状态 1（已支付）→ canRepurchase=true
expect "已支付订单可再次购买（canRepurchase=true）" "$(curl -s "$BASE/api/order/list?pageNum=1&pageSize=20" -H "$A13" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const o=JSON.parse(s).rows.find(x=>x.id==$OID15);console.log(o.canRepurchase)})")" "^true$"

# 2) 归属校验 + 登录态
expect "未登录再次购买 401" "$(curl -s -X POST $BASE/api/order/$OID15/repurchase)" '"code":401'
OTHER=$(curl -s -X POST $BASE/api/login -H "Content-Type: application/json" -d '{"code":"mock-openid-nobuy"}' | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.token)})")
expect "非本人订单再次购买被拒（归属校验）" "$(curl -s -X POST $BASE/api/order/$OID15/repurchase -H "Authorization: Bearer $OTHER")" "订单不存在"

# 3) 全成功：明细（菜品+规格+口味+数量）加入购物车
curl -s -X DELETE $BASE/api/cart/clear -H "$A13" > /dev/null
RP1=$(curl -s -X POST $BASE/api/order/$OID15/repurchase -H "$A13")
expect "全成功：successCount=1 且无跳过" "$(echo "$RP1" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(d.successCount+'-'+d.skipCount+'-'+d.allFailed)})")" "^1-0-false$"
expect "加购数量与规格口味正确写入购物车" "$(curl -s $BASE/api/cart/list -H "$A13" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const it=JSON.parse(s).data.find(i=>i.goodsId===1);console.log(it.quantity+'-'+(it.specName||'')+'-'+(it.flavors&&it.flavors.length?it.flavors[0].name+':'+it.flavors[0].values[0]:''))})")" "^2-.*辣度:微辣$"

# 4) 合并规则：再次购买同组合 → 数量累加（2+2=4）
curl -s -X POST $BASE/api/order/$OID15/repurchase -H "$A13" > /dev/null
expect "同菜品+同规格+同口味数量累加（2+2=4）" "$(curl -s $BASE/api/cart/list -H "$A13" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data.filter(i=>i.goodsId===1);console.log(d.length+'-'+d[0].quantity)})")" "^1-4$"

# 5) 每日限量库存不足 → 按剩余数量加购并提示（不整项跳过）
sql "update takeout.biz_goods set daily_limit_enabled=1, daily_limit_qty=1, auto_sold_out=0 where id=1" > /dev/null
sql "delete from takeout.biz_goods_stock_daily where goods_id=1" > /dev/null
sql "insert into takeout.biz_goods_stock_daily (goods_id, spec_id, stock_date, limit_qty, sold_qty, create_time, update_time) values (1, $SPEC_D12, curdate(), 1, 0, now(), now())" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$A13" > /dev/null
RP2=$(curl -s -X POST $BASE/api/order/$OID15/repurchase -H "$A13")
expect "库存不足按剩余量加购（原2份→加购1份并提示）" "$(echo "$RP2" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const a=JSON.parse(s).data.added[0];console.log(a.originQuantity+'-'+a.addedQuantity+'-'+a.quantityReduced)})")" "^2-1-true$"
sql "update takeout.biz_goods set daily_limit_enabled=0, daily_limit_qty=0 where id=1" > /dev/null
sql "delete from takeout.biz_goods_stock_daily where goods_id=1" > /dev/null

# 6) 下架 / 售罄 → 跳过并给出原因；全部失败时 allFailed=true
# 构造两条临时菜品（联调用，收尾删除），与菜1 组成三明细订单
sql "insert into takeout.biz_goods (id, category_id, name, image, price, description, status, sold_out, sales, sort, create_by, create_time) values (9002, 1, 'itest菜品B', '', 10.00, 'itest', 1, 0, 0, 2, 'admin', now()) on duplicate key update status=1, sold_out=0, auto_sold_out=0" > /dev/null
sql "insert into takeout.biz_goods (id, category_id, name, image, price, description, status, sold_out, sales, sort, create_by, create_time) values (9003, 1, 'itest菜品C', '', 12.00, 'itest', 1, 0, 0, 3, 'admin', now()) on duplicate key update status=1, sold_out=0, auto_sold_out=0" > /dev/null
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$A13" > /dev/null
curl -s -X POST $BASE/api/cart/add -H "$A13" -H "Content-Type: application/json" -d '{"goodsId":1,"specId":'"$SPEC_D12"',"quantity":1}' > /dev/null
curl -s -X POST $BASE/api/cart/add -H "$A13" -H "Content-Type: application/json" -d '{"goodsId":9002,"quantity":1}' > /dev/null
curl -s -X POST $BASE/api/cart/add -H "$A13" -H "Content-Type: application/json" -d '{"goodsId":9003,"quantity":1}' > /dev/null
sleep 5   # 防重锁：同一用户 5 秒内重复下单会被拒（takeout:order:lock）
O16=$(curl -s -X POST $BASE/api/order/create -H "$A13" -H "Content-Type: application/json" -d '{"deliveryType":1,"tableNo":"13"}')
OID16=$(jget "$O16" "data.orderId")
curl -s -X POST $BASE/api/pay/mock -H "$A13" -H "Content-Type: application/json" -d "{\"orderId\":$OID16}" > /dev/null
# 制造跳过场景：菜B 下架、菜C 售罄、菜1 正常
sql "update takeout.biz_goods set status=0 where id=9002" > /dev/null
sql "update takeout.biz_goods set sold_out=1 where id=9003" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$A13" > /dev/null
RP3=$(curl -s -X POST $BASE/api/order/$OID16/repurchase -H "$A13")
expect "部分成功：下架与售罄项被跳过（success=1 skip=2）" "$(echo "$RP3" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(d.successCount+'-'+d.skipCount+'-'+d.allFailed)})")" "^1-2-false$"
expect "跳过原因区分下架与售罄" "$RP3" "已下架"
expect "跳过原因包含售罄" "$RP3" "已售罄"
# 全部失败：把剩余的菜1也置售罄
sql "update takeout.biz_goods set sold_out=1 where id=1" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$A13" > /dev/null
expect "全部失败：allFailed=true 且未加入购物车" "$(curl -s -X POST $BASE/api/order/$OID16/repurchase -H "$A13" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(d.allFailed+'-'+d.successCount)})")" "^true-0$"
expect "全部失败时购物车保持为空（不跳转语义）" "$(curl -s $BASE/api/cart/list -H "$A13" | jsonlen "data")" "^0$"
sql "update takeout.biz_goods set sold_out=0 where id=1" > /dev/null
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null

# 7) 展示条件排除：待支付(0) / 已取消(6) / 已退款(refund=2)
sleep 5
curl -s -X POST $BASE/api/cart/add -H "$A13" -H "Content-Type: application/json" -d '{"goodsId":1,"specId":'"$SPEC_D12"',"quantity":1}' > /dev/null
O17=$(curl -s -X POST $BASE/api/order/create -H "$A13" -H "Content-Type: application/json" -d '{"deliveryType":1,"tableNo":"13"}')
OID17=$(jget "$O17" "data.orderId")
expect "待支付订单不可再次购买（canRepurchase=false 且后端拦截）" "$(curl -s -X POST $BASE/api/order/$OID17/repurchase -H "$A13")" "不支持再次购买"
expect "待支付订单列表 canRepurchase=false" "$(curl -s "$BASE/api/order/list?pageNum=1&pageSize=20" -H "$A13" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const o=JSON.parse(s).rows.find(x=>x.id==$OID17);console.log(o.canRepurchase)})")" "^false$"
# 已退款（refund_status=2）：把已支付订单 OID15 走完整退款通过流程后再校验
sleep 5
curl -s -X POST $BASE/api/cart/add -H "$A13" -H "Content-Type: application/json" -d '{"goodsId":1,"specId":'"$SPEC_D12"',"quantity":1}' > /dev/null
O19=$(curl -s -X POST $BASE/api/order/create -H "$A13" -H "Content-Type: application/json" -d '{"deliveryType":1,"tableNo":"13"}')
OID19=$(jget "$O19" "data.orderId")
curl -s -X POST $BASE/api/pay/mock -H "$A13" -H "Content-Type: application/json" -d "{\"orderId\":$OID19}" > /dev/null
cat > /tmp/it_t13_refund.json <<EOF
{"orderId":$OID19,"reason":"T13 联调退款"}
EOF
curl -s -X POST $BASE/api/refund/apply -H "$A13" -H "Content-Type: application/json" --data-binary @/tmp/it_t13_refund.json > /dev/null
RFID13=$(sql "select id from takeout.biz_order_refund where order_no=(select order_no from takeout.biz_order where id=$OID19) and audit_status=0")
curl -s -X PUT $BASE/merchant/refund/approve/$RFID13 -H "$A" > /dev/null
expect "已退款订单 refund_status=2" "$(sql "select refund_status from takeout.biz_order where id=$OID19")" "^2$"
expect "已退款订单不可再次购买（canRepurchase=false）" "$(curl -s "$BASE/api/order/list?pageNum=1&pageSize=20" -H "$A13" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const o=JSON.parse(s).rows.find(x=>x.id==$OID19);console.log(o.canRepurchase)})")" "^false$"
expect "已退款订单再次购买被后端拦截" "$(curl -s -X POST $BASE/api/order/$OID19/repurchase -H "$A13")" "不支持再次购买"
# 已取消（status=6）
expect "已取消订单列表 canRepurchase=false" "$(curl -s -X PUT $BASE/api/order/cancel/$OID17 -H "$A13")" '"code":200'; sleep 1
expect "已取消订单 canRepurchase=false" "$(curl -s "$BASE/api/order/list?pageNum=1&pageSize=20" -H "$A13" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const o=JSON.parse(s).rows.find(x=>x.id==$OID17);console.log(o.canRepurchase)})")" "^false$"

# 收尾：恢复菜品状态、删除联调临时菜品与联调用户产生的订单（避免污染 T14 基线与重复执行）
sql "update takeout.biz_goods set status=1, sold_out=0, auto_sold_out=0, daily_limit_enabled=0, daily_limit_qty=0 where id=1" > /dev/null
sql "delete from takeout.biz_goods where id in (9002,9003)" > /dev/null
sql "delete from takeout.biz_goods_stock_daily where goods_id=1" > /dev/null
sql "delete from takeout.biz_cart where member_id=(select id from takeout.biz_member where openid='mock-openid-itest13')" > /dev/null
sql "delete from takeout.biz_order_item where order_id in (select id from takeout.biz_order where member_id=(select id from takeout.biz_member where openid='mock-openid-itest13'))" > /dev/null
sql "delete from takeout.biz_payment_record where order_no in (select order_no from takeout.biz_order where member_id=(select id from takeout.biz_member where openid='mock-openid-itest13'))" > /dev/null
sql "delete from takeout.biz_order_refund where order_no in (select order_no from takeout.biz_order where member_id=(select id from takeout.biz_member where openid='mock-openid-itest13'))" > /dev/null
sql "delete from takeout.biz_order where member_id=(select id from takeout.biz_member where openid='mock-openid-itest13')" > /dev/null
docker exec takeout-redis redis-cli del "takeout:goods:list" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$A13" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$AUTH" > /dev/null

echo "==================== 14. 数据看板口径（T14，只读统计） ===================="
# 口径基准：营业额/订单量仅取主状态 5（已完成）按 create_time 统计；退款金额取全部 refund_status=2；
#           净额仅扣减"已完成且已退款"；排行/分类取明细聚合且排除已退款订单。
# 为避免受历史数据干扰，本段用"独立构造的今日数据 + 与独立 SQL 对账"的方式验证一致性。

# 0) 记录本段开始前的基线（用于后续增量断言）
BASE_REVENUE=$(sql "select format(ifnull(sum(case when status=5 then pay_amount else 0 end),0),2) from takeout.biz_order where create_time>=curdate() and create_time<date_add(curdate(),interval 1 day)")
BASE_ORDERS=$(sql "select count(case when status=5 then 1 end) from takeout.biz_order where create_time>=curdate() and create_time<date_add(curdate(),interval 1 day)")
BASE_GOODS=$(sql "select format(ifnull(sum(case when status=5 then goods_amount else 0 end),0),2) from takeout.biz_order where create_time>=curdate() and create_time<date_add(curdate(),interval 1 day)")

# 1) 概览：接口值必须与独立 SQL 完全一致（营业额/有效订单数/菜品收入）
OV14=$(curl -s "$BASE/merchant/dashboard/overview?range=today" -H "$A")
expect "概览营业额与独立 SQL 一致" "$(echo "$OV14" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(Number(d.revenue).toFixed(2))})")" "^$BASE_REVENUE$"
expect "概览有效订单数与独立 SQL 一致" "$(echo "$OV14" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(d.orderCount)})")" "^$BASE_ORDERS$"
expect "概览菜品收入与独立 SQL 一致" "$(echo "$OV14" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(Number(d.goodsRevenue).toFixed(2))})")" "^$BASE_GOODS$"
# 净额 = 营业额 - 退款扣减额；客单价 = 净额 / 有效订单数
expect "净营业额 = 营业额 - 退款扣减额" "$(echo "$OV14" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log((Number(d.revenue)-Number(d.refundedInRevenue)).toFixed(2)===Number(d.netRevenue).toFixed(2))})")" "^true$"
expect "客单价 = 净额/订单数（无单时为0）" "$(echo "$OV14" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;const exp=d.orderCount>0?(Number(d.netRevenue)/d.orderCount).toFixed(2):'0.00';console.log(exp===Number(d.avgOrderAmount).toFixed(2))})")" "^true$"

# 2) 构造：一笔已完成订单（走完流转），验证"仅已完成计入"（营业额与订单数各 +1 单）
T14=$(curl -s -X POST $BASE/api/login -H "Content-Type: application/json" -d '{"code":"mock-openid-itest14"}' | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.token)})")
A14="Authorization: Bearer $T14"
curl -s -X DELETE $BASE/api/cart/clear -H "$A14" > /dev/null
curl -s -X POST $BASE/api/cart/add -H "$A14" -H "Content-Type: application/json" -d '{"goodsId":1,"quantity":3}' > /dev/null
O20=$(curl -s -X POST $BASE/api/order/create -H "$A14" -H "Content-Type: application/json" -d '{"deliveryType":1,"tableNo":"14"}')
OID20=$(jget "$O20" "data.orderId")
# 未完成（待支付）时不应计入看板
expect "待支付订单不计入营业额" "$(curl -s "$BASE/merchant/dashboard/overview?range=today" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.orderCount)})")" "^$BASE_ORDERS$"
curl -s -X POST $BASE/api/pay/mock -H "$A14" -H "Content-Type: application/json" -d "{\"orderId\":$OID20}" > /dev/null
for st in accept ready complete; do curl -s -X PUT $BASE/merchant/order/$st/$OID20 -H "$A" > /dev/null; done
expect "已完成订单计入（有效订单数 +1）" "$(curl -s "$BASE/merchant/dashboard/overview?range=today" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.orderCount)})")" "^$((BASE_ORDERS+1))$"
expect "已完成订单计入营业额（+54.00）" "$(curl -s "$BASE/merchant/dashboard/overview?range=today" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(Number(JSON.parse(s).data.revenue).toFixed(2))})")" "^$(node -e "console.log((Number('$BASE_REVENUE')+54).toFixed(2))")$"

# 3) 退款口径：全部已退款计入"退款金额"；仅"已完成且已退款"扣减净额
REFUNDED_BASE=$(sql "select ifnull(sum(pay_amount),0) from takeout.biz_order where refund_status=2 and create_time>=curdate() and create_time<date_add(curdate(),interval 1 day)")
sleep 5
curl -s -X POST $BASE/api/cart/add -H "$A14" -H "Content-Type: application/json" -d '{"goodsId":1,"quantity":5}' > /dev/null
O21=$(curl -s -X POST $BASE/api/order/create -H "$A14" -H "Content-Type: application/json" -d '{"deliveryType":1,"tableNo":"14"}')
OID21=$(jget "$O21" "data.orderId")
curl -s -X POST $BASE/api/pay/mock -H "$A14" -H "Content-Type: application/json" -d "{\"orderId\":$OID21}" > /dev/null
cat > /tmp/it_t14_refund.json <<EOF
{"orderId":$OID21,"reason":"T14 看板口径联调退款"}
EOF
curl -s -X POST $BASE/api/refund/apply -H "$A14" -H "Content-Type: application/json" --data-binary @/tmp/it_t14_refund.json > /dev/null
RFID14=$(sql "select id from takeout.biz_order_refund where order_no=(select order_no from takeout.biz_order where id=$OID21) and audit_status=0")
curl -s -X PUT $BASE/merchant/refund/approve/$RFID14 -H "$A" > /dev/null
expect "退款金额含新退款单（口径为全部已退款）" "$(curl -s "$BASE/merchant/dashboard/overview?range=today" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(Number(d.refundedAmount).toFixed(2))})")" "^$(node -e "console.log((Number('$REFUNDED_BASE')+90).toFixed(2))")$"
expect "未完成订单退款不扣减净额（该单本不在营业额内）" "$(curl -s "$BASE/merchant/dashboard/overview?range=today" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(Number(d.refundedInRevenue).toFixed(2))})")" "^0.00$"
# 排行排除已退款订单明细（退款单 5 份不计）
expect "排行排除已退款订单明细" "$(curl -s "$BASE/merchant/dashboard/goodsRank?range=today&sortBy=quantity&topN=10" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const r=JSON.parse(s).data[0];console.log(r.quantity)})")" "^$(sql "select ifnull(sum(i.quantity),0) from takeout.biz_order_item i join takeout.biz_order o on o.id=i.order_id where o.status=5 and o.refund_status!=2 and o.create_time>=curdate() and o.create_time<date_add(curdate(),interval 1 day)")$"

# 4) 已完成+已退款 → 真正扣减净额（模拟"完成后退款成功"）
sql "update takeout.biz_order set refund_status=2 where id=$OID20" > /dev/null
OV14B=$(curl -s "$BASE/merchant/dashboard/overview?range=today" -H "$A")
expect "已完成+已退款：扣减额=54.00" "$(echo "$OV14B" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(Number(JSON.parse(s).data.refundedInRevenue).toFixed(2))})")" "^54.00$"
expect "已完成+已退款：净额=营业额-54" "$(echo "$OV14B" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log((Number(d.revenue)-54).toFixed(2)===Number(d.netRevenue).toFixed(2))})")" "^true$"
sql "update takeout.biz_order set refund_status=0 where id=$OID20" > /dev/null

# 5) 趋势：按天颗粒度空区间补零；按小时为 24 点
TREND_DAY=$(curl -s "$BASE/merchant/dashboard/trend?range=month&granularity=day" -H "$A")
expect "趋势按天：点数=本月已过天数" "$(echo "$TREND_DAY" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.length)})")" "^$(node -e "console.log(new Date().getDate())")$"
expect "趋势按天：含补零空点" "$(echo "$TREND_DAY" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.filter(p=>Number(p.orderCount)===0).length>0)})")" "^true$"
expect "趋势按小时：24 个点" "$(curl -s "$BASE/merchant/dashboard/trend?range=today&granularity=hour" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.length)})")" "^24$"
expect "趋势按小时：首点 00:00" "$(curl -s "$BASE/merchant/dashboard/trend?range=today&granularity=hour" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data[0].timeLabel.slice(-5))})")" "^00:00$"

# 6) 分类占比：百分比合计为 100（有数据时）
expect "分类占比百分比合计=100" "$(curl -s "$BASE/merchant/dashboard/categoryStat?range=last30" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(d.length?d.reduce((a,b)=>a+Number(b.percent),0).toFixed(2):'100.00')})")" "^100.00$"

# 7) 时间范围预设与参数兜底（非法 range / 起止倒置 / 越界 topN / 非法 sortBy 均不报错）
expect "非法 range 回落近7天（code=200）" "$(curl -s "$BASE/merchant/dashboard/overview?range=unknown" -H "$A")" '"code":200'
expect "非法 range 结果等于近7天" "$(curl -s "$BASE/merchant/dashboard/overview?range=unknown" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.orderCount)})")" "^$(curl -s "$BASE/merchant/dashboard/overview?range=last7" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(JSON.parse(s).data.orderCount)})")$"
expect "起止倒置回落近7天（code=200）" "$(curl -s "$BASE/merchant/dashboard/overview?range=custom&startDate=2026-09-27&endDate=2026-09-01" -H "$A")" '"code":200'
expect "越界 topN 不报错" "$(curl -s "$BASE/merchant/dashboard/goodsRank?range=last30&topN=500" -H "$A")" '"code":200'
expect "非法 sortBy 白名单回落（不报错）" "$(curl -s "$BASE/merchant/dashboard/goodsRank?range=last30&sortBy=drop_table" -H "$A")" '"code":200'
expect "无数据区间营业额定为 0 且不报错" "$(curl -s "$BASE/merchant/dashboard/overview?range=custom&startDate=2030-01-01&endDate=2030-01-02" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s).data;console.log(d.revenue+'-'+d.orderCount+'-'+d.avgOrderAmount)})")" "^0-0-0"

# 8) 履约方式筛选：堂食配送费收入为 0（配送费仅外卖）
expect "堂食筛选：配送费收入=0" "$(curl -s "$BASE/merchant/dashboard/overview?range=last30&deliveryType=1" -H "$A" | node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{console.log(Number(JSON.parse(s).data.deliveryRevenue).toFixed(2))})")" "^0.00$"
# 9) 权限：未登录访问看板接口应被拦截
expect "未登录访问看板接口被拦截（业务码 401）" "$(curl -s "$BASE/merchant/dashboard/overview?range=today")" '"code":401'

# 清理本段测试数据（订单及其明细/流水/退款单）
sql "delete from takeout.biz_order_refund where order_no in (select order_no from takeout.biz_order where id in ($OID20,$OID21))" > /dev/null
sql "delete from takeout.biz_payment_record where order_no in (select order_no from takeout.biz_order where id in ($OID20,$OID21))" > /dev/null
sql "delete from takeout.biz_order_item where order_id in ($OID20,$OID21)" > /dev/null
sql "delete from takeout.biz_order where id in ($OID20,$OID21)" > /dev/null
curl -s -X DELETE $BASE/api/cart/clear -H "$A14" > /dev/null

echo "==================== 结果统计 ===================="
echo "TOTAL=$TOTAL PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -eq 0 ]; then
  echo "联调回归：全部通过"
else
  echo "联调回归：存在失败项（见上方 FAIL 明细）"
fi
