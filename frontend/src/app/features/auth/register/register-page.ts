import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { RegisterRequestGenderEnum as Gender } from '../../../core/api';
import { AuthStore } from '../../../core/auth/auth-store';
import { fieldViolations, problemOf } from '../../../core/http/problem-detail';
import { NotificationService } from '../../../core/notification/notification.service';
import {
  applyFieldViolations,
  errorMessage,
  formEvents,
  setServerError,
} from '../../../shared/forms/form-errors';

/** Mirrors the backend rules (D18). */
const USERNAME_PATTERN = /^[A-Za-z0-9_-]+$/;
const USERNAME_TAKEN = "Nom d'utilisateur déjà pris";
const UNREACHABLE = 'Serveur injoignable. Vérifiez votre connexion et réessayez.';
const UNEXPECTED = 'Une erreur inattendue est survenue. Réessayez.';

interface GenderOption {
  label: string;
  value: Gender;
}

const GENDER_OPTIONS: GenderOption[] = [
  { label: 'Homme', value: Gender.MALE },
  { label: 'Femme', value: Gender.FEMALE },
  { label: 'Autre', value: Gender.OTHER },
];

/** Group rule, client-side only (the server never sees the confirmation). */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password: unknown = group.get('password')?.value;
  const confirm: unknown = group.get('confirmPassword')?.value;
  return confirm !== '' && confirm !== password ? { passwordMismatch: true } : null;
}

/** `/auth/register` (docs/migration-plan.md row 1, legacy `index.jsp` signup modal). */
@Component({
  selector: 'app-register-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    MessageModule,
    PasswordModule,
    SelectModule,
  ],
  templateUrl: './register-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly genderOptions = GENDER_OPTIONS;

  protected readonly form = this.fb.group(
    {
      username: [
        '',
        [
          Validators.required,
          Validators.minLength(3),
          Validators.maxLength(20),
          Validators.pattern(USERNAME_PATTERN),
        ],
      ],
      gender: this.fb.control<Gender | null>(null),
      age: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
      confirmPassword: ['', Validators.required],
      acceptTerms: [false, Validators.requiredTrue],
    },
    { validators: passwordsMatch },
  );

  protected readonly pending = signal(false);
  protected readonly formError = signal<string | null>(null);

  private readonly formEvents = formEvents(this.form);
  protected readonly errors = computed(() => {
    this.formEvents();
    const c = this.form.controls;
    return {
      username: errorMessage(c.username, {
        required: "Le nom d'utilisateur est obligatoire.",
        minlength: "Le nom d'utilisateur doit contenir au moins 3 caractères.",
        maxlength: "Le nom d'utilisateur doit contenir au plus 20 caractères.",
        pattern: 'Lettres, chiffres, « _ » et « - » uniquement.',
      }),
      age: errorMessage(c.age, {
        required: "L'âge est obligatoire.",
        min: "L'âge doit être d'au moins 1 an.",
      }),
      gender: errorMessage(c.gender, {}),
      password: errorMessage(c.password, {
        required: 'Le mot de passe est obligatoire.',
        minlength: 'Le mot de passe doit contenir au moins 8 caractères.',
        maxlength: 'Le mot de passe doit contenir au plus 72 caractères.',
      }),
      confirmPassword:
        errorMessage(c.confirmPassword, { required: 'Veuillez confirmer le mot de passe.' }) ??
        (c.confirmPassword.touched && this.form.hasError('passwordMismatch')
          ? 'Les mots de passe ne correspondent pas.'
          : null),
      acceptTerms: errorMessage(c.acceptTerms, {
        required: "Vous devez accepter les conditions d'utilisation.",
      }),
    };
  });

  protected async submit(): Promise<void> {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password, age, gender } = this.form.getRawValue();
    if (age === null) {
      return; // unreachable: `age` is required
    }
    this.pending.set(true);
    try {
      const player = await this.auth.register({ username, password, age, gender });
      this.notifications.success('Compte créé', 'Vous pouvez maintenant vous connecter.');
      await this.router.navigate(['/auth/login'], { queryParams: { username: player.username } });
    } catch (error: unknown) {
      this.showError(error);
    } finally {
      this.pending.set(false);
    }
  }

  private showError(error: unknown): void {
    const problem = problemOf(error);
    const status = error instanceof HttpErrorResponse ? error.status : null;
    if (status === 409) {
      setServerError(
        this.form.controls.username,
        problem?.detail ?? problem?.title ?? USERNAME_TAKEN,
      );
    } else if (status === 400) {
      const violations = fieldViolations(problem);
      const unmatched = applyFieldViolations(this.form, violations);
      if (violations.length === 0 || unmatched.length > 0) {
        this.formError.set(unmatched[0]?.message ?? problem?.detail ?? UNEXPECTED);
      }
    } else if (status === 0) {
      this.formError.set(UNREACHABLE);
    } else {
      this.formError.set(problem?.detail ?? UNEXPECTED);
    }
  }
}
