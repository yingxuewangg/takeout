<template>
   <div class="app-container takeout-page">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" class="takeout-search">
         <el-form-item label="分类名称" prop="name">
            <el-input v-model="queryParams.name" placeholder="请输入分类名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="分类状态" clearable style="width: 200px">
               <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="takeout-toolbar">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['goods:category:add']">新增</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="categoryList" class="takeout-table">
         <el-table-column label="分类ID" prop="id" width="90" />
         <el-table-column label="分类名称" prop="name" :show-overflow-tooltip="true" />
         <el-table-column label="排序" prop="sort" width="100" />
         <el-table-column label="状态" align="center" width="110">
            <template #default="scope">
               <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
            </template>
         </el-table-column>
         <el-table-column label="创建时间" align="center" prop="createTime" min-width="175" />
         <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="190">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['goods:category:edit']">修改</el-button>
               <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['goods:category:remove']">删除</el-button>
            </template>
         </el-table-column>
      </el-table>

      <!-- 添加或修改分类对话框 -->
      <el-dialog :title="title" v-model="open" width="500px" append-to-body>
         <el-form ref="categoryRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="分类名称" prop="name">
               <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="32" />
            </el-form-item>
            <el-form-item label="排序" prop="sort">
               <el-input-number v-model="form.sort" controls-position="right" :min="0" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
               <el-radio-group v-model="form.status">
                  <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
               </el-radio-group>
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

<script setup lang="ts" name="Category">
import { listCategory, getCategory, addCategory, updateCategory, delCategory } from "@/api/merchant/category"
import type { BizCategory } from '@/types/api/merchant/goods'

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict("sys_normal_disable")

const categoryList = ref<BizCategory[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const title = ref<string>("")

const data = reactive({
   form: {} as BizCategory,
   queryParams: {
      name: undefined,
      status: undefined
   } as Partial<BizCategory>,
   rules: {
      name: [{ required: true, message: "分类名称不能为空", trigger: "blur" }],
      sort: [{ required: true, message: "显示顺序不能为空", trigger: "blur" }]
   }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询分类列表 */
function getList() {
   loading.value = true
   listCategory(queryParams.value).then(response => {
      categoryList.value = response.data || []
      loading.value = false
   })
}

/** 取消按钮 */
function cancel() {
   open.value = false
   reset()
}

/** 表单重置 */
function reset() {
   form.value = { id: undefined, name: undefined, sort: 0, status: "0" }
   proxy.resetForm("categoryRef")
}

/** 搜索按钮操作 */
function handleQuery() {
   getList()
}

/** 重置按钮操作 */
function resetQuery() {
   proxy.resetForm("queryRef")
   handleQuery()
}

/** 新增按钮操作 */
function handleAdd() {
   reset()
   open.value = true
   title.value = "添加分类"
}

/** 修改按钮操作 */
function handleUpdate(row?: BizCategory) {
   reset()
   getCategory(row!.id!).then(response => {
      form.value = response.data!
      open.value = true
      title.value = "修改分类"
   })
}

/** 提交按钮 */
function submitForm() {
   proxy.$refs["categoryRef"].validate((valid: boolean) => {
      if (valid) {
         if (form.value.id != undefined) {
            updateCategory(form.value).then(() => {
               proxy.$modal.msgSuccess("修改成功")
               open.value = false
               getList()
            })
         } else {
            addCategory(form.value).then(() => {
               proxy.$modal.msgSuccess("新增成功")
               open.value = false
               getList()
            })
         }
      }
   })
}

/** 删除按钮操作 */
function handleDelete(row?: BizCategory) {
   proxy.$modal.confirm('是否确认删除分类"' + row!.name + '"？').then(function () {
      return delCategory(row!.id!)
   }).then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
   }).catch(() => {})
}

getList()
</script>
