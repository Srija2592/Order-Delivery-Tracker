export interface LoginResponse {
  authenticationToken: string;
  refreshToken: string;
  expiresAt: Date;
  username: string;
  role:string[];
}

export interface RefreshTokenPayload{
  refreshToken:string;
  username:string;
}
