import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { SkeletonModule } from 'primeng/skeleton';
import { PlayerProfileDto, UpdateProfileRequestGenderEnum as Gender } from '../../core/api';
import { fieldViolations, problemOf } from '../../core/http/problem-detail';
import { NotificationService } from '../../core/notification/notification.service';
import { applyFieldViolations, errorMessage, formEvents } from '../../shared/forms/form-errors';
import { ProfileStore } from './profile-store';

const GENDER_OPTIONS: { label: string; value: Gender }[] = [
  { label: 'Homme', value: Gender.MALE },
  { label: 'Femme', value: Gender.FEMALE },
  { label: 'Autre', value: Gender.OTHER },
];

/** The profile DTO and the update request each have their own (identical) gender enum. */
function toGender(value: string | null | undefined): Gender | null {
  return GENDER_OPTIONS.find((option) => option.value === value)?.value ?? null;
}

const UNEXPECTED = 'Une erreur inattendue est survenue. Réessayez.';

/**
 * Profile view and edit (legacy profile.jsp): username read-only, age and gender editable,
 * game stats read-only. No click ratios (D12), no "change password" (D19).
 */
@Component({
  selector: 'app-profile-page',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    AvatarModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    SkeletonModule,
  ],
  templateUrl: './profile-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePage {
  private readonly store = inject(ProfileStore);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly profile = this.store.profile;
  protected readonly loadFailed = this.store.loadFailed;
  protected readonly genderOptions = GENDER_OPTIONS;
  protected readonly initial = computed(
    () => this.profile()?.username.charAt(0).toUpperCase() ?? '',
  );

  protected readonly form = this.fb.group({
    age: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    gender: this.fb.control<Gender | null>(null),
  });

  protected readonly pending = signal(false);
  protected readonly formError = signal<string | null>(null);

  private readonly formEvents = formEvents(this.form);
  protected readonly errors = computed(() => {
    this.formEvents();
    return {
      age: errorMessage(this.form.controls.age, {
        required: "L'âge est obligatoire.",
        min: "L'âge doit être d'au moins 1 an.",
      }),
      gender: errorMessage(this.form.controls.gender, {}),
    };
  });
  protected readonly unchanged = computed(() => {
    this.formEvents();
    return this.form.pristine;
  });

  constructor() {
    void this.store.load().then(() => this.resetForm(this.profile()));
  }

  protected retry(): void {
    void this.store.load().then(() => this.resetForm(this.profile()));
  }

  protected cancel(): void {
    this.formError.set(null);
    this.resetForm(this.profile());
  }

  protected async submit(): Promise<void> {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { age, gender } = this.form.getRawValue();
    if (age === null) {
      return; // unreachable: `age` is required
    }
    this.pending.set(true);
    try {
      const profile = await this.store.update({ age, gender });
      this.resetForm(profile);
      this.notifications.success('Modification réussie !');
    } catch (error: unknown) {
      this.showError(error);
    } finally {
      this.pending.set(false);
    }
  }

  private resetForm(profile: PlayerProfileDto | null): void {
    if (profile) {
      this.form.reset({ age: profile.age, gender: toGender(profile.gender) });
    }
  }

  private showError(error: unknown): void {
    const problem = problemOf(error);
    if (error instanceof HttpErrorResponse && error.status === 400) {
      const violations = fieldViolations(problem);
      const unmatched = applyFieldViolations(this.form, violations);
      if (violations.length === 0 || unmatched.length > 0) {
        this.formError.set(unmatched[0]?.message ?? problem?.detail ?? UNEXPECTED);
      }
    } else {
      this.formError.set(problem?.detail ?? UNEXPECTED);
    }
  }
}
