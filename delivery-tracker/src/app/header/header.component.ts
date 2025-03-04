import { ChangeDetectorRef, Component, OnChanges, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { LoginResponse } from 'src/app/auth/login/loginresponse';
import { UserService } from '../services/user.service';
import { AuthService } from '../services/auth.service';
import { AuthGuard } from '../auth/auth.guard';
import { MatDialog } from '@angular/material/dialog';
import { UpdateComponent } from '../update/update.component';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css'],
})
export class HeaderComponent implements OnInit, OnChanges {
  isLoggedIn!: boolean;
  username!: any;
  user: any = {
    name: '',
    username: '',
    email: '',
    id: 0
  };
  loginResponse: LoginResponse = {
    username: '',
    authenticationToken: '',
    expiresAt: new Date(),
    refreshToken: '',
    roles: [],
  };
  constructor(
    private router: Router,
    private authservice: AuthService,
    public dialog: MatDialog,
    private userService: UserService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.authservice.isLoggedIn().subscribe((data: boolean) => {
      this.isLoggedIn = data;
      this.cdr.detectChanges();
    });

    this.username = this.authservice.getUserName();

    if (!this.username) {
      this.username = this.authservice.getUserName();
    }
  }

  ngOnChanges() {
      this.authservice.isLoggedIn().subscribe((data: boolean) => {
        this.isLoggedIn = data;
        this.cdr.detectChanges();
      });

      this.username = this.authservice.getUserName();

      if (!this.username) {
        this.username = this.authservice.getUserName();

    }
  }

  logout() {
    this.authservice.logout();
    this.isLoggedIn = false;
    this.router.navigateByUrl('/login');
  }

  update(): void {
    if (this.username) {
      this.userService.getUser(this.username).subscribe({
        next: (data) => {
          this.dialog.open(UpdateComponent, {
            data: data,
            height: '400px',
            width: '800px',
          });
        },
        error: (err) => console.error('Error fetching user for update:', err),
      });
    }
  }


  profile(username: string) {
    console.log('Navigating to: ', username);
    this.router.navigateByUrl('/profile');
  }

}
