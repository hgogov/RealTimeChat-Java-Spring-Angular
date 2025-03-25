import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    HttpClientModule,
    MatSnackBarModule
  ],
  template: `<router-outlet></router-outlet>`,
  styles: []
})
export class AppComponent {
  title = 'Chat Application';
}
