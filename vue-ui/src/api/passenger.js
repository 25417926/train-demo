import instance from "@/api/interceptor";

export const getPassengerList = async () => {
    try {
        const res = await instance.get("/member/Passenger/Query-List");
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const addPassenger = async (data) => {
    try {
        const res = await instance.post("/member/Passenger/save",data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deletePassenger = async (data) => {
    try {
        const res = await instance.post("/member/Passenger/delete",data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}