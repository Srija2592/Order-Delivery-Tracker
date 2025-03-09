import { inject, NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TrackerComponent } from './tracker/tracker.component';
import { AuthService } from './services/auth.service';
import { LoginComponent } from './auth/login/login.component';
import { ProfileComponent } from './profile/profile.component';
import { UnauthorizedPageComponent } from './unauthorized-page/unauthorized-page.component';
import { SignupComponent } from './auth/signup/signup.component';
import { HomeComponent } from './home/home.component';
import { DeliveryOrdersComponent } from './delivery-orders/delivery-orders.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent }, // ✅ Moved before wildcard
  { path: '', component: HomeComponent },
  { path: 'delivery-order', component: DeliveryOrdersComponent },
  {
    path: 'profile',
    component: ProfileComponent
  },
  // {
  //   path: 'order-detail/:orderId',
  //   component: OrderDetailComponent,
  //   canActivate: [() => inject(AuthService).isLoggedIn()],
  // },
  {
    path: 'tracker',
    component: TrackerComponent,
    canActivate: [() => inject(AuthService).isLoggedIn()],
  },
  { path: 'unauthorized', component: UnauthorizedPageComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
