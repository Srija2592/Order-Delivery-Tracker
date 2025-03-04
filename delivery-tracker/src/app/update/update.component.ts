import { Component, Inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { UserService } from '../services/user.service';
import { AuthService } from '../services/auth.service';
import { User } from '../models/user';

@Component({
  selector: 'app-update',
  templateUrl: './update.component.html',
  styleUrls: ['./update.component.css']
})
export class UpdateComponent implements OnInit{

  updateuserform!:FormGroup;
  updatedata!:User;
  submitted:boolean=false;

  user:any;

  constructor(@Inject(MAT_DIALOG_DATA) public data: User,public dialogref:MatDialogRef<UpdateComponent>,private userservice:UserService,private authService:AuthService){
    this.updatedata={
      username:data.username,
      name:data.name,
      role:data.role,
      email:data.email,
      id:data.id
    };
  }

  ngOnInit(): void {

    this.updateuserform=new FormGroup({

      username:new FormControl(this.authService.getUserName(),Validators.required),
      name:new FormControl(this.data.name,Validators.required),
      role:new FormControl(this.data.role,Validators.required),
      email:new FormControl(this.data.email,Validators.required)

    });

  }

  updateuserdata(){
    if(this.submitted==false){
        this.updatedata.username=this.updateuserform.get('username')?.value;
        this.updatedata.name=this.updateuserform.get('name')?.value;
        this.updatedata.role=this.updateuserform.get('role')?.value;
        this.updatedata.role;
        this.updatedata.email=this.updateuserform.get('email')?.value;
        console.log(this.updatedata);
        this.userservice.update(this.updatedata,this.updatedata.username).subscribe(data=>{this.user=data,
          this.submitted=true,
          this.dialogref.close();

        })

    }}

}
