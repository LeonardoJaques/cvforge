import { Routes } from '@angular/router';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { EditorComponent } from './components/editor/editor.component';
import { PreviewComponent } from './components/preview/preview.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'resume/new', component: EditorComponent },
  { path: 'resume/:id', component: EditorComponent },
  { path: 'preview/:id', component: PreviewComponent },
  { path: '**', redirectTo: '' }
];
