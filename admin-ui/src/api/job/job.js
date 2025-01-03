import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getJobList = async () => {
    try {
        const res = await instance.get("/batch/job/query",);

        if(res.status === 200) {
            return res.data

        }else{
            message.error(res.data.message)
        }
    } catch (e) {
        console.log(e);
    }
}
export const deleteJob = async (name, group) => {
    try {
        const res = await instance.post("/batch/job/delete",{name, group});
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const addJob = async (url,job) => {
    try {
        const res = await instance.post("/batch/job/"+ url, job);
        console.log(url)
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const PauseJob = async (name,group) => {
    try {
        const res = await instance.post("/batch/job/pause",{name, group});
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const ResumeJob = async (record) => {
    try {
        const res = await instance.post("/batch/job/reschedule",record);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const RunJob = async (record) => {
    try {
        const res = await instance.post("/batch/job/run",record);
        return res.data
    } catch (e) {
        console.log(e);
    }
}