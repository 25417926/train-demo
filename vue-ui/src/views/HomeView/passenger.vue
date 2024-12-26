<template>
  <div aria-hidden="false">
    <a-button type="primary" @click="showModal">新增乘车人</a-button>
    <a-modal v-model:visible="visible" title="乘车人信息" @ok="handleOk">
      <template #footer>
        <a-button key="back" @click="handleCancel">关闭</a-button>
        <a-button key="submit" type="primary" :loading="loading" @click="handleOk">提交</a-button>
      </template>
      <p>&emsp;姓&emsp;名:&nbsp;<a-input v-model:value="User.name" placeholder="请输入姓名" style="width: 80%"/>
      </p>
      <p>身份证号:&nbsp;<a-input v-model:value="User.idCard" placeholder="请输入身份证号" style="width: 80%"/>
      </p>
      <p> &emsp;类&emsp;型:&nbsp;<a-select
          ref="select"
          v-model:value="User.type"
          style="width: 80%"
      >
        <a-select-option value="1">成人</a-select-option>
        <a-select-option value="2">学生</a-select-option>
        <a-select-option value="3">儿童</a-select-option>
        <a-select-option value="4 ">残疾军人</a-select-option>
      </a-select>
      </p>
    </a-modal>
  </div>
  <!-- 使用v-for指令来循环渲染User数组中的每一项 -->
  <a-table bordered :data-source="List" :columns="columns">
    <template #bodyCell="{ column,  record }">
      <template v-if="column.dataIndex === 'name'">
        <div>{{ record.name }}</div>
      </template>
      <template v-else-if="column.dataIndex === 'operation'">
        <a-popconfirm
            title="Sure to delete?"
            @click="onDelete(record.id)"
        >
          <a >Delete</a>
        </a-popconfirm>
      </template>
    </template>
    <template>
      <a-pagination v-model:current="current" :total="50" show-less-items />
    </template>
  </a-table>
</template>

<script>
import { reactive, ref } from "vue";

import {addPassenger, deletePassenger, getPassengerList} from "@/api/passenger"
import {message} from "ant-design-vue";
export default {
  name: "PassengerView",
  setup() {
    const columns = [{
      title: 'name',
      dataIndex: 'name',
      width: '20%',
    }, {
      title: 'idCard',
      dataIndex: 'idCard',
      width: '20%',
    }, {
      title: 'type',
      dataIndex: 'type',
      width: '20%',
    },{
      title: 'operation',
      dataIndex: 'operation',
      width: '20%',
    }];
    // 使用reactive正确定义User对象，无需使用.value访问属性
    const User = reactive({
      name: null,
      idCard: null,
      type: null
    });
    let List =ref([])
    const loading = ref(false);
    const visible = ref(false);
    const showModal = () => {
      visible.value = true;
    };
    const handleOk = async () => {
      const res =  await addPassenger(User);
      console.log(User);
      loading.value = true;
      console.log(res);
      if (res.code === 200) {
          loading.value = false;
          visible.value = false;
          message.success(res.message)
      }else {
        setTimeout(() => {
          loading.value = false;
          User.name = null;
          User.idCard = null;
          User.type = null;
          message.error('添加失败')
        }, 2000)
      }
    };
    const handleCancel = () => {
      visible.value = false;
    };
    const getList=async () =>{
      const res = await getPassengerList();
      List.value = res.content
      console.log(List.value)
    }
    const onDelete = async(id) => {
      const res = await deletePassenger(id);
      if (res.code === 200) {
        message.success(res.message)
      }
    }
    return {
      User,
      loading,
      visible,
      showModal,
      handleOk,
      handleCancel,
      getList,
      List,
      columns,
      onDelete
    };
  },
  mounted() {
    this.getList();
  }
}
</script>

<style scoped>

</style>