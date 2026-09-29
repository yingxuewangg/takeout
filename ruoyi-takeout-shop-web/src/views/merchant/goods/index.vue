<template>
   <div class="app-container takeout-page">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" class="takeout-search">
         <el-form-item label="菜品名称" prop="name">
            <el-input v-model="queryParams.name" placeholder="请输入菜品名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="分类" prop="categoryId">
            <el-select v-model="queryParams.categoryId" placeholder="所属分类" clearable style="width: 200px">
               <el-option v-for="c in categoryList" :key="c.id" :label="c.name" :value="c.id!" />
            </el-select>
         </el-form-item>
         <el-form-item label="上架状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="上架状态" clearable style="width: 160px">
               <el-option label="在售" value="1" />
               <el-option label="下架" value="0" />
            </el-select>
         </el-form-item>
         <el-form-item label="售罄" prop="soldOut">
            <el-select v-model="queryParams.soldOut" placeholder="售罄标记" clearable style="width: 160px">
               <el-option label="已售罄" value="1" />
               <el-option label="未售罄" value="0" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="takeout-toolbar">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['goods:goods:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="warning" plain icon="SoldOut" :disabled="multiple" @click="handleSoldOut('1')" v-hasPermi="['goods:goods:soldOut']">标记售罄</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Sell" :disabled="multiple" @click="handleSoldOut('0')" v-hasPermi="['goods:goods:soldOut']">取消售罄</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['goods:goods:remove']">删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="goodsList" @selection-change="handleSelectionChange" class="takeout-table">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="图片" align="center" width="90">
            <template #default="scope">
               <image-preview :src="scope.row.image" :width="48" :height="48" />
            </template>
         </el-table-column>
         <el-table-column label="菜品名称" prop="name" :show-overflow-tooltip="true" />
         <el-table-column label="分类" align="center" min-width="110">
            <template #default="scope">
               {{ categoryName(scope.row.categoryId) }}
            </template>
         </el-table-column>
         <el-table-column label="价格(元)" align="center" prop="price" width="105" />
         <el-table-column label="销量" align="center" prop="sales" width="85" />
         <el-table-column label="售罄" align="center" width="90">
            <template #default="scope">
               <el-switch
                  :model-value="scope.row.soldOut"
                  active-value="1"
                  inactive-value="0"
                  :disabled="!checkPermi(['goods:goods:soldOut'])"
                  @change="(val: string) => handleSoldOutChange(scope.row, val)"
               />
            </template>
         </el-table-column>
         <el-table-column label="上架状态" align="center" width="100">
            <template #default="scope">
               <el-switch
                  :model-value="scope.row.status"
                  active-value="1"
                  inactive-value="0"
                  :disabled="!checkPermi(['goods:goods:shelf'])"
                  @change="(val: string) => handleStatusChange(scope.row, val)"
               />
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['goods:goods:edit']">修改</el-button>
               <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['goods:goods:remove']">删除</el-button>
            </template>
         </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

      <!-- 添加或修改菜品对话框 -->
      <el-dialog :title="title" v-model="open" width="760px" append-to-body>
         <el-form ref="goodsRef" :model="form" :rules="rules" label-width="100px">
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="菜品名称" prop="name">
                     <el-input v-model="form.name" placeholder="请输入菜品名称" maxlength="64" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="所属分类" prop="categoryId">
                     <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
                        <el-option v-for="c in categoryList" :key="c.id" :label="c.name" :value="c.id!" />
                     </el-select>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="售价(元)" prop="price">
                     <el-input-number v-model="form.price" :precision="2" :min="0" :max="9999" controls-position="right" style="width: 100%" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="排序" prop="sort">
                     <el-input-number v-model="form.sort" controls-position="right" :min="0" style="width: 100%" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="菜品图片" prop="image">
               <image-upload v-model="form.image" :limit="1" action="/merchant/file/upload" :file-type="['png', 'jpg', 'jpeg', 'webp']" />
               <div class="el-form-item-msg">支持 jpg/jpeg/png/webp，单张不超过 5MB；入库只保存相对路径</div>
            </el-form-item>
            <el-form-item label="菜品描述" prop="description">
               <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入菜品描述" maxlength="500" />
            </el-form-item>

            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="每日限量">
                     <el-switch v-model="form.dailyLimitEnabled" active-value="1" inactive-value="0" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="每日限量值" v-if="form.dailyLimitEnabled === '1'">
                     <el-input-number v-model="form.dailyLimitQty" controls-position="right" :min="0" style="width: 100%" />
                  </el-form-item>
               </el-col>
            </el-row>
            <div class="el-form-item-msg" v-if="form.dailyLimitEnabled === '1'" style="margin-bottom: 18px">
               菜品级限量用于无规格菜品；规格行单独填「每日限量」时以规格值为准（留空则回落菜品级）。库存耗尽自动售罄，可在「今日库存」页查看与补货。
            </div>

            <el-form-item label="规格">
               <el-table :data="form.specs" size="small" border style="width: 100%">
                  <el-table-column label="规格名" width="180">
                     <template #default="scope">
                        <el-input v-model="scope.row.name" placeholder="如大份/小份" maxlength="32" />
                     </template>
                  </el-table-column>
                  <el-table-column label="差价(元)">
                     <template #default="scope">
                        <el-input-number v-model="scope.row.priceDelta" :precision="2" :min="-999" :max="999" controls-position="right" style="width: 100%" />
                     </template>
                  </el-table-column>
                  <el-table-column label="排序" width="130">
                     <template #default="scope">
                        <el-input-number v-model="scope.row.sort" controls-position="right" :min="0" style="width: 100%" />
                     </template>
                  </el-table-column>
                  <el-table-column label="售罄" width="90" align="center">
                     <template #default="scope">
                        <el-switch v-model="scope.row.soldOut" active-value="1" inactive-value="0" />
                     </template>
                  </el-table-column>
                  <el-table-column label="每日限量" width="130" align="center">
                     <template #default="scope">
                        <el-input-number
                           v-model="scope.row.dailyLimitQty"
                           controls-position="right"
                           :min="0"
                           placeholder="留空用菜品级"
                           style="width: 100%"
                        />
                     </template>
                  </el-table-column>
                  <el-table-column label="操作" width="80" align="center">
                     <template #default="scope">
                        <el-button link type="danger" icon="Delete" @click="form.specs!.splice(scope.$index, 1)">删除</el-button>
                     </template>
                  </el-table-column>
               </el-table>
               <el-button type="primary" plain icon="Plus" size="small" @click="addSpec" style="margin-top: 6px">添加规格</el-button>
               <div class="el-form-item-msg">规格级售罄：售罄规格用户端不可选；全部规格售罄时该菜品按售罄展示（菜品级售罄优先）</div>
            </el-form-item>

            <el-form-item label="口味">
               <el-table :data="form.flavors" size="small" border style="width: 100%">
                  <el-table-column label="组名" width="150">
                     <template #default="scope">
                        <el-input v-model="scope.row.name" placeholder="如辣度" maxlength="32" />
                     </template>
                  </el-table-column>
                  <el-table-column label="选项(逗号分隔)">
                     <template #default="scope">
                        <el-input v-model="scope.row.optionsText" placeholder="如 微辣,中辣,特辣" />
                     </template>
                  </el-table-column>
                  <el-table-column label="选择方式" width="130" align="center">
                     <template #default="scope">
                        <el-select v-model="scope.row.selectType" style="width: 100%">
                           <el-option label="单选" value="0" />
                           <el-option label="多选" value="1" />
                        </el-select>
                     </template>
                  </el-table-column>
                  <el-table-column label="排序" width="130">
                     <template #default="scope">
                        <el-input-number v-model="scope.row.sort" controls-position="right" :min="0" style="width: 100%" />
                     </template>
                  </el-table-column>
                  <el-table-column label="操作" width="80" align="center">
                     <template #default="scope">
                        <el-button link type="danger" icon="Delete" @click="form.flavors!.splice(scope.$index, 1)">删除</el-button>
                     </template>
                  </el-table-column>
               </el-table>
               <el-button type="primary" plain icon="Plus" size="small" @click="addFlavor" style="margin-top: 6px">添加口味组</el-button>
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="Goods">
import { listGoods, getGoods, addGoods, updateGoods, delGoods, changeGoodsStatus, changeSoldOut } from "@/api/merchant/goods"
import { listCategory } from "@/api/merchant/category"
import { checkPermi } from "@/utils/permission"
import type { BizGoods, BizCategory, BizGoodsSpec, BizGoodsFlavor, GoodsQueryParams } from '@/types/api/merchant/goods'

const { proxy } = getCurrentInstance()

const goodsList = ref<BizGoods[]>([])
const categoryList = ref<BizCategory[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

/** 口味行：optionsText 为界面编辑用逗号分隔文本 */
type FlavorRow = BizGoodsFlavor & { optionsText?: string }

const data = reactive({
   form: { specs: [], flavors: [] } as BizGoods,
   queryParams: {
      pageNum: 1,
      pageSize: 10,
      name: undefined,
      categoryId: undefined,
      status: undefined,
      soldOut: undefined
   } as GoodsQueryParams,
   rules: {
      name: [{ required: true, message: "菜品名称不能为空", trigger: "blur" }],
      categoryId: [{ required: true, message: "所属分类不能为空", trigger: "change" }],
      price: [{ required: true, message: "售价不能为空", trigger: "blur" }]
   }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询菜品列表 */
function getList() {
   loading.value = true
   listGoods(queryParams.value).then(response => {
      goodsList.value = response.rows
      total.value = response.total
      loading.value = false
   })
}

/** 查询分类（下拉与列名显示） */
function getCategoryList() {
   listCategory().then(response => {
      categoryList.value = response.data || []
   })
}

function categoryName(categoryId?: number): string {
   return categoryList.value.find(c => c.id === categoryId)?.name || "-"
}

/** 表单重置 */
function reset() {
   form.value = { id: undefined, name: undefined, categoryId: undefined, image: undefined, price: undefined, description: undefined, status: "1", soldOut: "0", autoSoldOut: "0", dailyLimitEnabled: "0", dailyLimitQty: 0, sort: 0, specs: [], flavors: [] }
   proxy.resetForm("goodsRef")
}

/** 取消按钮 */
function cancel() {
   open.value = false
   reset()
}

/** 搜索按钮操作 */
function handleQuery() {
   queryParams.value.pageNum = 1
   getList()
}

/** 重置按钮操作 */
function resetQuery() {
   proxy.resetForm("queryRef")
   handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: BizGoods[]) {
   ids.value = selection.map(item => item.id!)
   multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
   reset()
   open.value = true
   title.value = "添加菜品"
}

/** 修改按钮操作 */
function handleUpdate(row?: BizGoods) {
   reset()
   const id = row?.id || ids.value[0]
   getGoods(id).then(response => {
      const goods = response.data!
      // 口味 JSON 数组转逗号分隔文本便于编辑
      const flavors = (goods.flavors || []).map(f => ({ ...f, optionsText: parseOptionsText(f.options) }))
      form.value = { ...goods, specs: goods.specs || [], flavors: flavors as FlavorRow[] } as BizGoods
      open.value = true
      title.value = "修改菜品"
   })
}

/** 提交按钮 */
function submitForm() {
   proxy.$refs["goodsRef"].validate((valid: boolean) => {
      if (valid) {
         // 口味文本转 JSON 数组字符串入库
         const flavors = (form.value.flavors as FlavorRow[]).map(f => ({ ...f, options: toJSONOptions(f.optionsText) }))
         const payload = { ...form.value, flavors } as BizGoods
         if (payload.id != undefined) {
            updateGoods(payload).then(() => {
               proxy.$modal.msgSuccess("修改成功")
               open.value = false
               getList()
            })
         } else {
            addGoods(payload).then(() => {
               proxy.$modal.msgSuccess("新增成功")
               open.value = false
               getList()
            })
         }
      }
   })
}

/** 删除按钮操作 */
function handleDelete(row?: BizGoods) {
   const goodsIds = row?.id || ids.value
   proxy.$modal.confirm('是否确认删除所选菜品？').then(function () {
      return delGoods(goodsIds)
   }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
   }).catch(() => {})
}

/** 上下架开关 */
function handleStatusChange(row: BizGoods, val: string) {
   const text = val === "1" ? "上架" : "下架"
   proxy.$modal.confirm('确认将菜品"' + row.name + '"调整为' + text + '？').then(function () {
      return changeGoodsStatus(row.id!, val)
   }).then(() => {
      proxy.$modal.msgSuccess(text + "成功")
      getList()
   }).catch(() => {
      getList()
   })
}

/** 行内售罄开关 */
function handleSoldOutChange(row: BizGoods, val: string) {
   changeSoldOut([row.id!], val).then(() => {
      proxy.$modal.msgSuccess(val === "1" ? "已标记售罄" : "已取消售罄")
      getList()
   })
}

/** 批量售罄/取消售罄 */
function handleSoldOut(soldOut: string) {
   const text = soldOut === "1" ? "标记为售罄" : "取消售罄"
   proxy.$modal.confirm('是否确认将所选 ' + ids.value.length + ' 个菜品' + text + '？').then(function () {
      return changeSoldOut(ids.value, soldOut)
   }).then(() => {
      getList()
      proxy.$modal.msgSuccess("操作成功")
   }).catch(() => {})
}

/** 添加规格行 */
function addSpec() {
   (form.value.specs as BizGoodsSpec[]).push({ name: undefined, priceDelta: 0, sort: (form.value.specs?.length || 0) + 1, soldOut: "0", dailyLimitQty: undefined })
}

/** 添加口味组行 */
function addFlavor() {
   (form.value.flavors as FlavorRow[]).push({ name: undefined, optionsText: "", selectType: "0", sort: (form.value.flavors?.length || 0) + 1 })
}

/** JSON数组字符串 -> 逗号分隔文本 */
function parseOptionsText(options?: string): string {
   if (!options) return ""
   try {
      const arr = JSON.parse(options)
      return Array.isArray(arr) ? arr.join(",") : options
   } catch {
      return options
   }
}

/** 逗号分隔文本 -> JSON数组字符串 */
function toJSONOptions(text?: string): string {
   if (!text || !text.trim()) return "[]"
   return JSON.stringify(text.split(",").map(s => s.trim()).filter(s => s))
}

getCategoryList()
getList()
</script>
