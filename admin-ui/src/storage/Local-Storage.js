// Local-Storage.js文件内容

export const LocalStorageUtil = {
    setItem: function (key, value) {
        try {
            if (typeof value === 'object') {
                value = JSON.stringify(value);
            }
            localStorage.setItem(key, value);
            return true;
        } catch (error) {
            console.log('存储数据到localStorage失败:', error);
            return false;
        }
    },
    getItem: function (key) {
        const data = localStorage.getItem(key);
        try {
            return JSON.parse(data);
        } catch (error) {
            return data;
        }
    },
    removeItem: function (key) {
        localStorage.removeItem(key);
    },
    clear: function () {
        localStorage.clear();
    }
};