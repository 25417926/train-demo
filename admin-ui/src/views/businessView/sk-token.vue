  <template>
    <p>
      <a-space>
        <a-button type="primary" @click="handleQuery()">刷新</a-button>
<!--        <a-button type="primary" @click="onAdd">新增</a-button>-->
      </a-space>
    </p>
    <a-table :dataSource="sk_Tokens"
             :columns="columns"
             :pagination="pagination"
             @change="handleTableChange"
             :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operation'">
          <a-space>
<!--            <a-popconfirm-->
<!--                title="删除后不可恢复，确认删除?"-->
<!--                @confirm="onDelete(record.id)"-->
<!--                ok-text="确认" cancel-text="取消">-->
<!--              <a style="color: red">删除</a>-->
<!--            </a-popconfirm>-->
            <a @click="onEdit(record)">修改令牌余量</a>
          </a-space>
        </template>
      </template>
    </a-table>
    <a-modal v-model:visible="visible" title="秒杀令牌" @ok="handleOk"
             ok-text="确认" cancel-text="取消">
      <a-form :model="sk_Token" :label-col="{span: 4}" :wrapper-col="{ span: 20 }">
        <a-form-item label="日期">
          <a-date-picker v-model:value="sk_Token.date" valueFormat="YYYY-MM-DD" placeholder="请选择日期" disabled/>
        </a-form-item>
        <a-form-item label="车次编号">
          <a-input v-model:value="sk_Token.trainCode" disabled/>
        </a-form-item>
        <a-form-item label="令牌余量">
          <a-input v-model:value="sk_Token.count" />
        </a-form-item>
      </a-form>
    </a-modal>
  </template>

  <script>
  import { defineComponent, ref, onMounted } from 'vue';
  import {message} from "ant-design-vue";
  import {addSk_Token, deleteSk_Token, getSk_TokenList} from "@/api/business/Sk_Token"
  export default defineComponent({
    name: "sk-token-view",
    setup() {
      const visible = ref(false);
      let sk_Token = ref({
        id: undefined,
        date: undefined,
        trainCode: undefined,
        count: undefined,
        createTime: undefined,
        updateTime: undefined,
      });
      const sk_Tokens = ref([]);
      // 分页的三个属性名是固定的
      const pagination = ref({
        total: 0,
        current: 1,
        pageSize: 10,
      });
      let loading = ref(false);
      const columns = [
      {
        title: '日期',
        dataIndex: 'date',
        key: 'date',
      },
      {
        title: '车次编号',
        dataIndex: 'trainCode',
        key: 'trainCode',
      },
      {
        title: '令牌余量',
        dataIndex: 'count',
        key: 'count',
      },
      {
        title: '操作',
        dataIndex: 'operation'
      }
      ];

      const onAdd = () => {
        sk_Token.value = {};
        visible.value = true;
      };

      const onEdit = (record) => {
        sk_Token.value = window.Tool.copy(record);
        visible.value = true;
      };
      const onDelete = async (id) => {
        try {
          const res = await deleteSk_Token(id);
          if (res.code === 200) {
            message.success(res.message);
            handleQuery(); // 刷新数据
          }
        } catch (error) {
          message.error('删除失败');
        }
      };
      const handleOk = async () => {
        loading.value = true;
        try {
          const res = await addSk_Token(sk_Token.value);
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

      const handleQuery = async (param = {page: 1, size: pagination.value.pageSize}) => {
        const res = await getSk_TokenList(param);
        if (res.code === 200) {
          sk_Tokens.value = res.content.list;
          pagination.value.total = res.total;
        }
      };

      const handleTableChange = (page) => {
        // console.log("看看自带的分页参数都有啥：" + JSON.stringify(page));
        pagination.value.current = page.current;
        handleQuery({
          page: page.current,
          size: page.pageSize
        });
      };

      onMounted(() => {
        handleQuery({
          page: 1,
          size: pagination.value.pageSize
        });
      });

      return {
        sk_Token,
        visible,
        sk_Tokens,
        pagination,
        columns,
        handleTableChange,
        handleQuery,
        loading,
        onAdd,
        handleOk,
        onEdit,
        onDelete
      };
    },
  });
  </script>
