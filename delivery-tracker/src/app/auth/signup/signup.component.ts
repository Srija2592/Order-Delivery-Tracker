import { Component, OnInit } from '@angular/core';
import { SignupRequest } from './signuprequest';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { Router } from '@angular/router';
import { AuthService } from 'src/app/services/auth.service';
import { ToastrService } from 'ngx-toastr';


@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.css']
})
export class SignupComponent implements OnInit{
  signupRequest: SignupRequest;
  signupForm!: FormGroup;

  constructor( private router: Router,private toastr: ToastrService,private authService:AuthService) {
    this.signupRequest = {

      email: '',
      password: '',
      username:''
    };
  }

  ngOnInit() {
    this.signupForm = new FormGroup({

      email: new FormControl('', [Validators.required, Validators.email]),
      password: new FormControl('', Validators.required),
      username:new FormControl('',Validators.required)
    });
  }

  signup() {
    this.signupRequest.email = this.signupForm.get('email')?.value;
    this.signupRequest.password = this.signupForm.get('password')?.value;
    this.signupRequest.username=this.signupForm.get('username')?.value;

    this.authService.signup(this.signupRequest).subscribe((data) => {
        this.router.navigate(['/login'],


        { queryParams: { registered: 'true' } }), this.toastr.success('Signup Successful');
      }, error => {
        console.log(error);
        this.toastr.error('Registration Failed! Please try again');

      });
  }


}
