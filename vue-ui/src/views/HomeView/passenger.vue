<template>
  <div aria-hidden="false">
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
        <a-button type="primary" @click="OnAdd">新增乘车人</a-button>
      </a-space>
    </p>
    <a-modal v-model:visible="visible" title="乘车人信息" @ok="handleOk">
      <template #footer>
        <a-button key="back" @click="handleCancel">关闭</a-button>
        <a-button key="submit" type="primary" :loading="loading" @click="handleOk">提交</a-button>
      </template>
      <p>&emsp;姓&emsp;名:&nbsp;<a-input v-model:value="User.name" placeholder="请输入姓名" style="width: 80%"/></p>
      <p>身份证号:&nbsp;<a-input v-model:value="User.idCard" placeholder="请输入身份证号" style="width: 80%"/></p>
      <p> &emsp;类&emsp;型:&nbsp;<a-select ref="select" v-model:value="User.type" style="width: 80%">
        <a-select-option value="1">成人</a-select-option>
        <a-select-option value="2">学生</a-select-option>
        <a-select-option value="3">儿童</a-select-option>
        <a-select-option value="4">残疾军人</a-select-option>
      </a-select>
      </p>
    </a-modal>
    <a-table
      bordered
      :data-source="List"
      :columns="columns"
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operation'">
          <a-space>
            <a @click="OnEdit(record)">update</a>
            <a-popconfirm
              title="删除后无法恢复，确认删除?"
              @confirm="onDelete(record.id)"
              ok-button="确认" cancel-button="取消"
            >
              <a style="color: red">Delete</a>
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

export default {
  name: "PassengerView",
  setup() {
    const columns = [
      { title: 'name', dataIndex: 'name', width: '20%' },
      { title: 'idCard', dataIndex: 'idCard', width: '20%' },
      { title: 'type', dataIndex: 'type', width: '20%' },
      { title: 'operation', dataIndex: 'operation', width: '20%' },
    ];
    const User = ref({
      id: null,
      memberId: null,
      name: null,
      idCard: null,
      type: null,
      createTime: undefined,
      updateTime: undefined,
    });
    const pagination = ref({
      total: 0,
      current: 1,
      pageSize: 2,
    });
    const List = ref([]);
    const loading = ref(false);
    const visible = ref(false);

    const OnAdd = () => {
      User.value = {};
      visible.value = true;
    };

    const handleOk = async () => {
      loading.value = true;
      try {
        const res = await addPassenger(User.value);
        if (res.code === 200) {
          visible.value = false;
          message.success(res.message);
          handleQuery(); // 刷新数据
        } else {
          message.error('添加失败');
        }
      } catch (error) {
        message.error('添加失败');
      } finally {
        loading.value = false;
      }
    };

    const handleCancel = () => {
      visible.value = false;
    };

    const handleTableChange = (pagination) => {
      handleQuery({ page: pagination.current, size: pagination.pageSize });
    };

    const getList = async (param) => {
      loading.value = true;
      try {
        const res = await getPassengerList(param);
        if (res.code === 200) {
          pagination.value.total = res.content.total;
          pagination.value.current = res.content.page;
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
        if (res.code === 200) {
          message.success(res.message);
          handleQuery(); // 刷新数据
        }
      } catch (error) {
        message.error('删除失败');
      }
    };

    const handleQuery = async (param = { page: 1, size: pagination.value.pageSize }) => {
      getList(param);
    };

    const OnEdit = (record) => {
      User.value = { ...record }; // 使用对象展开运算符复制对象
      visible.value = true;
    };

    const OnClean = () => {
      User.value = {
        id: null,
        memberId: null,
        name: null,
        idCard: null,
        type: null,
      };
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
      OnAdd,
      handleOk,
      handleCancel,
      getList,
      onDelete,
      handleTableChange,
      handleQuery,
      OnEdit,
      OnClean,
    };
  },
};
</script>

<style scoped>
</style>
