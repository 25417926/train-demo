<template>
  <!-- 列车选择器组件 -->
  <a-select
      v-model:value="Name"
      show-search allow-clear
      :filter-option="filterNameOption"
      @change="onChange"
      placeholder="请选择车次"
  >
    <!-- 列车选项 -->
    <a-select-option v-for="item in names" :key="item.name" :value="item.name" :label="`${item.name}${item.namePinyin}${item.namePy}`">
      {{ item.name }}| {{ item.namePinyin }} | {{ item.namePy }}
    </a-select-option>
  </a-select>
</template>

<script>
import { defineComponent, onMounted, ref, watch } from "vue";
import { getStationList } from "@/api/train_station";

export default defineComponent({
  name: "TrainDropBox",
  // 父组件传入的属性
  props: {
    modelValue: String,
  },
  // 自定义事件
  emits: ["update:modelValue", "Change"],

  setup(props, { emit }) {
    // 列车列表
    const names = ref([]);
    // 选中的列车代码
    const Name = ref("");
    // 列车代码映射表
    const nameMap = ref({});

    /**
     * 过滤列车代码的函数
     * @param {string} input - 用户输入的过滤关键词
     * @param {object} option - 列车选项
     * @returns {boolean} - 是否匹配过滤条件
     */
    const filterNameOption = (input, option) => {
      return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0;
    };

    /**
     * 获取所有列车信息
     */
    const getAllNames = async () => {
      try {
        const res = await getStationList();
        if (res.code === 200) {
          names.value = res.content;

          nameMap.value = res.content.reduce((acc, item) => {

            acc[item.id] = item;
            return acc;
          }, {});

        }
      } catch (error) {
        console.error("Failed to fetch name list:", error);
      }
    };

    /**
     * 当选中的列车代码发生变化时的处理函数
     * @param {string} value - 新选中的列车代码
     */
    const onChange = (value) => {
      emit("update:modelValue", value);
      const name = nameMap.value[value] || {};
      // console.log("Change", value);
      // console.log("nameMap",nameMap.value);
      // if (!Object.keys(name).length) {
      //   console.warn("No matching name found for code:", value);
      // }
      emit("Change", name);
    };

    // 在组件挂载时获取所有列车信息
    onMounted(() => {
      getAllNames();
    });

    // 监听modelValue的变化，并同步到Name
    watch(
      () => props.modelValue,
      (newValue) => {
        Name.value = newValue;
      }
    );

    return {
      names,
      Name,
      filterNameOption,
      getAllNames,
      onChange,
    };
  },
});
</script>

<style scoped>
</style>
