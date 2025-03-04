export interface User{
  username:string;
  role:any;
  name:string;
  email:string;
  id:number;
}
export enum Role {
  USER, ADMIN
}
