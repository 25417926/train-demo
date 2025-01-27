<template>
  <!-- 列车选择器组件 -->
  <a-select
      v-model:value="trainCode"
      show-search allow-clear
      :filter-option="filterTrainCodeOption"
      @change="onChange"
      placeholder="请选择列车"
  >
    <!-- 列车选项 -->
    <a-select-option v-for="item in trains" :key="item.code" :value="item.code" :label="`${item.code}${item.type}${item.start}${item.end}`">
      {{ item.code }}&emsp;{{ item.type }} | {{ item.start }}~{{ item.end }}
    </a-select-option>
  </a-select>
</template>

<script>
import { defineComponent, onMounted, ref, watch } from "vue";
import { getTrainList } from "@/api/base/train_station";
import {useStore} from 'vuex'
export default defineComponent({
  name: "TrainDropBox",
  // 父组件传入的属性
  props: {
    modelValue: String,
  },
  // 自定义事件
  emits: ["update:modelValue", "Change"],

  setup(props, { emit }) {
    // 获取store
    const store = useStore()
    // 列车列表
    const trains = ref([]);
    // 选中的列车代码
    const trainCode = ref("");
    // 列车代码映射表
    const trainMap = ref({});

    /**
     * 过滤列车代码的函数
     * @param {string} input - 用户输入的过滤关键词
     * @param {object} option - 列车选项
     * @returns {boolean} - 是否匹配过滤条件
     */
    const filterTrainCodeOption = (input, option) => {
      return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0;
    };

    /**
     * 获取所有列车信息
     */
    const getAllTrains = async () => {
      let list = store.state.Session_params.params
      if (window.Tool.isNotEmpty(list)) {
        console.log("读取缓存")
        trains.value = list
        trainMap.value = createTrainMap(trains.value);
      } else {
        try {
          const res = await getTrainList();
          if (res.code === 200) {
            trains.value = res.content;
            console.log("保存缓存")
            store.commit('Session_params/set_params', res.content)
            trainMap.value = createTrainMap(trains.value);
          }
        } catch (error) {
          console.error("Failed to fetch train list:", error);
        }
      }
    };
    // 创建列车代码映射表
    const createTrainMap = (trainList) => {
      return trainList.reduce((acc, item) => {
        acc[item.code] = item;
        return acc;
      }, {});
    };
    /**
     * 当选中的列车代码发生变化时的处理函数
     * @param {string} value - 新选中的列车代码
     */
    const onChange = (value) => {
      emit("update:modelValue", value);
      const train = trainMap.value[value] || {};
      if (!Object.keys(train).length) {
        console.warn("No matching train found for code:", value);
      }
      emit("Change", train);
    };

    // 在组件挂载时获取所有列车信息
    onMounted(() => {
      getAllTrains();
    });

    // 监听modelValue的变化，并同步到trainCode
    watch(
      () => props.modelValue,
      (newValue) => {
        trainCode.value = newValue;
      }
    );

    return {
      trains,
      trainCode,
      filterTrainCodeOption,
      getAllTrains,
      onChange,
    };
  },
});
</script>

<style scoped>
</style>
