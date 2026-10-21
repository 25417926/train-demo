<template>
  <div aria-hidden="false">
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
        <a-button type="primary" @click="OnAdd">新增乘车人</a-button>
      </a-space>
    </p>
    <a-modal
      v-model:visible="visible"
      :title="User.id ? '编辑乘车人' : '新增乘车人'"
      :confirm-loading="loading"
      :mask-closable="false"
      ok-text="提交"
      cancel-text="关闭"
      width="520px"
      @ok="handleOk"
      @cancel="handleCancel">
      <a-form :model="User" layout="horizontal" :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
        <a-form-item label="姓名">
          <a-input v-model:value="User.name" placeholder="请输入姓名" allow-clear/>
        </a-form-item>
        <a-form-item label="身份证号">
          <a-input v-model:value="User.idCard" placeholder="请输入身份证号" allow-clear/>
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="User.type" placeholder="请选择乘客类型">
            <a-select-option value="1">成人</a-select-option>
            <a-select-option value="2">学生</a-select-option>
            <a-select-option value="3">儿童</a-select-option>
            <a-select-option value="4">残疾军人</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-table
      bordered
      :data-source="List"
      :columns="columns"
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'type'">
          {{ TYPE_MAP[record.type] || record.type }}
        </template>
        <template v-if="column.dataIndex === 'operation'">
          <a-space>
            <a @click="OnEdit(record)">编辑</a>
            <a-popconfirm
              title="删除后无法恢复，确认删除?"
              @confirm="onDelete(record.id)"
              ok-text="确认" cancel-text="取消"
            >
              <a style="color: red">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script>
import { ref, onMounted } from "vue";
import { addPassenger, deletePassenger, getPassengerList } from "@/api/passenger";
import { message } from "ant-design-vue";

const TYPE_MAP = { '1': '成人', '2': '学生', '3': '儿童', '4': '残疾军人' };

export default {
  name: "PassengerView",
  setup() {
    const columns = [
      { title: '姓名', dataIndex: 'name', width: '20%' },
      { title: '身份证号', dataIndex: 'idCard', width: '30%' },
      { title: '类型', dataIndex: 'type', width: '20%' },
      { title: '操作', dataIndex: 'operation', width: '20%' },
    ];
    const emptyUser = () => ({
      id: null,
      memberId: null,
      name: null,
      idCard: null,
      type: null,
      createTime: undefined,
      updateTime: undefined,
    });
    const User = ref(emptyUser());
    const pagination = ref({
      total: 0,
      current: 1,
      pageSize: 5,
    });
    const List = ref([]);
    const loading = ref(false);
    const visible = ref(false);

    const OnAdd = () => {
      User.value = emptyUser();
      visible.value = true;
    };

    const handleOk = async () => {
      if (!User.value.name) {
        message.warning('请输入姓名');
        return;
      }
      if (!User.value.idCard) {
        message.warning('请输入身份证号');
        return;
      }
      if (!User.value.type) {
        message.warning('请选择乘客类型');
        return;
      }
      loading.value = true;
      try {
        const res = await addPassenger(User.value);
        if (res && res.code === 200) {
          visible.value = false;
          message.success(res.message || '保存成功');
          handleQuery();
        } else {
          message.error((res && res.message) || '保存失败');
        }
      } catch (error) {
        message.error('保存失败');
      } finally {
        loading.value = false;
      }
    };

    const handleCancel = () => {
      visible.value = false;
    };

    const handleTableChange = (pag) => {
      handleQuery({ page: pag.current, size: pag.pageSize });
    };

    const getList = async (param) => {
      loading.value = true;
      try {
        const res = await getPassengerList(param);
        if (res && res.code === 200) {
          pagination.value.total = res.content.total;
          pagination.value.current = res.content.page;
          pagination.value.pageSize = res.content.size || pagination.value.pageSize;
          List.value = res.content.list;
        } else {
          message.error("刷新失败");
        }
      } catch (error) {
        message.error("刷新失败");
      } finally {
        loading.value = false;
      }
    };

    const onDelete = async (id) => {
      try {
        const res = await deletePassenger(id);
        if (res && res.code === 200) {
          message.success(res.message || '删除成功');
          handleQuery();
        } else {
          message.error((res && res.message) || '删除失败');
        }
      } catch (error) {
        message.error('删除失败');
      }
    };

    const handleQuery = async (param = { page: 1, size: pagination.value.pageSize }) => {
      getList(param);
    };

    const OnEdit = (record) => {
      User.value = { ...record };
      visible.value = true;
    };

    onMounted(() => {
      handleQuery();
    });

    return {
      User,
      loading,
      visible,
      pagination,
      List,
      columns,
      TYPE_MAP,
      OnAdd,
      handleOk,
      handleCancel,
      getList,
      onDelete,
      handleTableChange,
      handleQuery,
      OnEdit,
    };
  },
};
</script>

<style scoped>
</style>
