import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { AuthStore } from '../../../core/auth/auth-store';
import { fieldViolations, problemOf } from '../../../core/http/problem-detail';
import { applyFieldViolations, errorMessage, formEvents } from '../../../shared/forms/form-errors';

const DEFAULT_REDIRECT = '/lobby';
const BAD_CREDENTIALS = "Nom d'utilisateur ou mot de passe invalide.";
const UNREACHABLE = 'Serveur injoignable. Vérifiez votre connexion et réessayez.';
const UNEXPECTED = 'Une erreur inattendue est survenue. Réessayez.';

/** `/auth/login` (docs/migration-plan.md row 2, legacy `index.jsp` login modal). */
@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    InputTextModule,
    PasswordModule,
    MessageModule,
  ],
  templateUrl: './login-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage implements OnInit {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /** Query param: where `authGuard` sent the player from. */
  readonly returnUrl = input<string>();
  /** Query param: prefilled after registration. */
  readonly username = input<string>();

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected readonly pending = signal(false);
  protected readonly formError = signal<string | null>(null);

  private readonly formEvents = formEvents(this.form);
  protected readonly errors = computed(() => {
    this.formEvents();
    const { username, password } = this.form.controls;
    return {
      username: errorMessage(username, { required: "Le nom d'utilisateur est obligatoire." }),
      password: errorMessage(password, { required: 'Le mot de passe est obligatoire.' }),
    };
  });

  ngOnInit(): void {
    const username = this.username();
    if (username) {
      this.form.controls.username.setValue(username);
    }
  }

  protected async submit(): Promise<void> {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.pending.set(true);
    try {
      await this.auth.login(this.form.getRawValue());
      await this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
    } catch (error: unknown) {
      this.showError(error);
    } finally {
      this.pending.set(false);
    }
  }

  private showError(error: unknown): void {
    const problem = problemOf(error);
    const unmatched = applyFieldViolations(this.form, fieldViolations(problem));
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.formError.set(problem?.detail ?? BAD_CREDENTIALS);
    } else if (error instanceof HttpErrorResponse && error.status === 0) {
      this.formError.set(UNREACHABLE);
    } else if (error instanceof HttpErrorResponse && error.status === 400) {
      this.formError.set(unmatched[0]?.message ?? problem?.detail ?? UNEXPECTED);
    } else {
      this.formError.set(problem?.detail ?? UNEXPECTED);
    }
  }
}

/** Only in-app paths: never an absolute or protocol-relative URL (open redirect). */
function safeReturnUrl(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : DEFAULT_REDIRECT;
}
