<template>
   <div class="app-container takeout-page">
      <el-card>
         <template #header>
            <span>店铺信息（单店）</span>
         </template>
         <el-form ref="shopRef" :model="form" :rules="rules" label-width="110px" class="shop-form">
         <el-row :gutter="24">
            <el-col :span="12"><el-form-item label="店铺名称" prop="shopName">
               <el-input v-model="form.shopName" placeholder="请输入店铺名称" maxlength="64" />
            </el-form-item></el-col>
            <el-col :span="12"><el-form-item label="店铺位置" prop="address">
               <el-input v-model="form.address" placeholder="请输入店铺文字地址" maxlength="255" />
            </el-form-item></el-col>
            <el-form-item label="经纬度" prop="longitude">
               <el-col :span="11">
                  <el-input v-model="form.longitude" placeholder="经度，如 113.361991" />
               </el-col>
               <el-col :span="2" style="text-align: center">-</el-col>
               <el-col :span="11">
                  <el-input v-model="form.latitude" placeholder="纬度，如 23.124674" />
               </el-col>
            </el-form-item>
            <el-col :span="12"><el-form-item label="营业时间" prop="businessHours">
               <el-input v-model="form.businessHours" placeholder="如 09:00-21:00" maxlength="100" />
            </el-form-item></el-col>
            <el-col :span="12"><el-form-item label="商家电话" prop="phone">
               <el-input v-model="form.phone" placeholder="用户申请退款等场景可见" maxlength="20" />
            </el-form-item></el-col>
            <el-form-item label="营业状态" prop="businessStatus">
               <el-switch
                  v-model="form.businessStatus"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="营业中"
                  inactive-text="已打烊"
               />
               <div class="el-form-item-msg">打烊后用户端可浏览菜单但不可下单</div>
            </el-form-item>
            <el-col :span="12"><el-form-item label="外卖配送费" prop="deliveryFee">
               <el-input-number v-model="form.deliveryFee" :precision="2" :min="0" :max="999" controls-position="right" />
               <span style="margin-left: 8px">元</span>
            </el-form-item></el-col>
            <el-col :span="12"><el-form-item label="外卖起送价" prop="minDeliveryAmount">
               <el-input-number v-model="form.minDeliveryAmount" :precision="2" :min="0" :max="9999" controls-position="right" />
               <span style="margin-left: 8px">元</span>
               <div class="el-form-item-msg">用户菜品合计未达起送价时不可下单；下单时配送费按此快照</div>
            </el-form-item></el-col>
            <el-form-item label="店铺公告" prop="notice">
               <el-input v-model="form.notice" type="textarea" :rows="3" placeholder="展示在小程序首页" maxlength="500" />
            </el-form-item>
            <el-form-item>
               <el-button type="primary" @click="submitForm" v-hasPermi="['merchant:shop:edit']">保 存</el-button>
            </el-form-item>
                  </el-row>
      </el-form>
      </el-card>
   </div>
</template>

<script setup lang="ts" name="Shop">
import { getShopInfo, updateShopInfo } from "@/api/merchant/shop"
import type { BizShopInfo } from '@/types/api/merchant/goods'

const { proxy } = getCurrentInstance()

const form = ref<BizShopInfo>({})
const rules = {
   shopName: [{ required: true, message: "店铺名称不能为空", trigger: "blur" }],
   address: [{ required: true, message: "店铺位置不能为空", trigger: "blur" }],
   businessHours: [{ required: true, message: "营业时间不能为空", trigger: "blur" }],
   phone: [{ required: true, message: "商家电话不能为空", trigger: "blur" }],
   deliveryFee: [{ required: true, message: "外卖配送费不能为空", trigger: "blur" }],
   minDeliveryAmount: [{ required: true, message: "外卖起送价不能为空", trigger: "blur" }]
}

/** 查询店铺信息 */
function getShop() {
   getShopInfo().then(response => {
      form.value = response.data || {}
   })
}

/** 保存 */
function submitForm() {
   proxy.$refs["shopRef"].validate((valid: boolean) => {
      if (valid) {
         updateShopInfo(form.value).then(() => {
            proxy.$modal.msgSuccess("保存成功")
         })
      }
   })
}

getShop()
</script>
