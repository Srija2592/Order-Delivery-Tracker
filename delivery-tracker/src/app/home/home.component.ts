import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css'],
})
export class HomeComponent implements OnInit {
  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      const role = this.authService.getUserRole();
      console.log(role)
      if (role[0] === 'ROLE_ADMIN') {
        this.router.navigate(['/admin']);
      } else if (role[0] === 'ROLE_USER') {
        this.router.navigate(['/']);
      } else {
        this.router.navigate(['/delivery-order']);
      }
    } else {
      this.router.navigate(['/unauthorized']);
    }
  }
}
