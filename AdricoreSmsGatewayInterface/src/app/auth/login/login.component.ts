import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { MessagesModule } from 'primeng/messages';
import { MessageModule } from 'primeng/message';
import { CheckboxModule } from 'primeng/checkbox';
import { DividerModule } from 'primeng/divider';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { PLATFORM_ID } from '@angular/core';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, InputTextModule, PasswordModule, ButtonModule, CardModule, MessagesModule, MessageModule, CheckboxModule, DividerModule, InputGroupModule, InputGroupAddonModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  form; // initialized in constructor
  loading = false;
  error = signal<string | null>(null);
  showPassword = signal(false);
  private readonly platformId = inject(PLATFORM_ID);
  captchaValue = signal<string>('');
  private readonly animationDurationMs = 450;

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.form = this.fb.nonNullable.group({
      username: '',
      password: '',
      remember: false,
      captchaInput: ''
    });
  }

  ngOnInit() {
    this.form.get('username')?.addValidators([Validators.required, Validators.minLength(3)]);
    this.form.get('password')?.addValidators([Validators.required, Validators.minLength(4)]);
    this.form.get('captchaInput')?.addValidators([Validators.required]);
    this.generateCaptcha();
    if (isPlatformBrowser(this.platformId)) {
      const remembered = localStorage.getItem('rememberedUsername');
      if (remembered) this.form.patchValue({ username: remembered, remember: true });
    }
  }

  get f() { return this.form.controls; }

  togglePassword() { this.showPassword.set(!this.showPassword()); }

  generateCaptcha() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 5; i++) code += chars[Math.floor(Math.random() * chars.length)];
    this.captchaValue.set(code);
    this.form.patchValue({ captchaInput: '' });
    if (isPlatformBrowser(this.platformId)) {
      // animate & draw after next tick
      setTimeout(() => {
        this.addCaptchaAnimation();
        this.drawCaptcha();
      }, 10);
    }
  }
  drawCaptcha() {
    if (!isPlatformBrowser(this.platformId)) return;
    const canvas = document.getElementById('captchaCanvas') as HTMLCanvasElement | null;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const w = canvas.width; const h = canvas.height;
    // clear
    ctx.clearRect(0,0,w,h);
    // background gradient
    const grad = ctx.createLinearGradient(0,0,w,h);
    grad.addColorStop(0,'#f8fafc');
    grad.addColorStop(1,'#e2e8f0');
    ctx.fillStyle = grad;
    ctx.fillRect(0,0,w,h);
    // light noise dots
    for (let i=0;i<70;i++) {
      ctx.fillStyle = `rgba(0,0,0,${Math.random()*0.08})`;
      ctx.fillRect(Math.random()*w, Math.random()*h, 1.2, 1.2);
    }
    // random lines
    for (let i=0;i<5;i++) {
      ctx.strokeStyle = `rgba(55,65,81,${0.15+Math.random()*0.25})`;
      ctx.lineWidth = 1 + Math.random()*1.2;
      ctx.beginPath();
      ctx.moveTo(Math.random()*w, Math.random()*h);
      ctx.lineTo(Math.random()*w, Math.random()*h);
      ctx.stroke();
    }
    // draw characters
    const text = this.captchaValue();
    const charSpace = w / (text.length + 1);
    ctx.textBaseline = 'middle';
    ctx.font = '600 30px "Inter", Arial, sans-serif';
    for (let i=0;i<text.length;i++) {
      const ch = text[i];
      const x = charSpace*(i+1);
      const y = h/2 + (Math.random()*10 -5);
      const angle = (Math.random()*40 -20) * Math.PI/180;
      ctx.save();
      ctx.translate(x,y);
      ctx.rotate(angle);
      const hue = 200 + Math.random()*80;
      ctx.fillStyle = `hsl(${hue} 60% 35%)`;
      ctx.shadowColor = 'rgba(0,0,0,0.2)';
      ctx.shadowBlur = 4;
      ctx.fillText(ch, -10, 0);
      ctx.restore();
    }
    // subtle overlay wave
    ctx.globalCompositeOperation = 'overlay';
    ctx.fillStyle = 'rgba(255,255,255,0.15)';
    ctx.beginPath();
    ctx.moveTo(0,h*0.6);
    for (let x=0; x<=w; x+=10) {
      const yy = h*0.6 + Math.sin(x/25 + Date.now()/900)*8;
      ctx.lineTo(x, yy);
    }
    ctx.lineTo(w,h); ctx.lineTo(0,h); ctx.closePath();
    ctx.fill();
    ctx.globalCompositeOperation = 'source-over';
  }
  addCaptchaAnimation() {
    const wrap = document.querySelector('.captcha-canvas-wrap');
    if (!wrap) return;
    wrap.classList.remove('captcha-refresh');
    // force reflow
    void (wrap as HTMLElement).offsetWidth;
    wrap.classList.add('captcha-refresh');
    setTimeout(() => wrap.classList.remove('captcha-refresh'), this.animationDurationMs + 50);
  }
  submit() {
    this.error.set(null);
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    if (this.form.value.captchaInput?.toUpperCase() !== this.captchaValue()) {
      this.error.set('Captcha mismatch');
      this.generateCaptcha();
      return;
    }
    this.loading = true;
    const { username, password, remember } = this.form.value;
    const ok = this.auth.login(username!, password!);
    if (isPlatformBrowser(this.platformId)) {
      if (remember) localStorage.setItem('rememberedUsername', username!); else localStorage.removeItem('rememberedUsername');
    }
    setTimeout(() => {
      this.loading = false;
      if (!ok) { this.error.set('Invalid credentials'); this.generateCaptcha(); return; }
      this.router.navigate(['/home']);
    }, 300);
  }
}
