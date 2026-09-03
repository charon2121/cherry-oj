import { Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';

import { FieldDescription } from '@/components/ui/field';
import { IconButton } from '@/components/ui/icon-button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

type PasswordFieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: 'current-password' | 'new-password';
  minLength?: number;
  invalid?: boolean;
  errorDescriptionId?: string;
};

export function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete,
  minLength = 1,
  invalid = false,
  errorDescriptionId,
}: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const capsLockId = `${id}-caps-lock`;
  const describedBy = [invalid ? errorDescriptionId : undefined, capsLock ? capsLockId : undefined]
    .filter(Boolean)
    .join(' ');

  return (
    <div className="grid min-w-0 gap-[var(--ds-space-2)]">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Input
          id={id}
          name={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          required
          minLength={minLength}
          maxLength={128}
          aria-invalid={invalid}
          aria-describedby={describedBy || undefined}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyUp={(event) => setCapsLock(event.getModifierState('CapsLock'))}
          onKeyDown={(event) => setCapsLock(event.getModifierState('CapsLock'))}
          onBlur={() => setCapsLock(false)}
          className="pr-[var(--ds-space-12)]"
        />
        <IconButton
          aria-pressed={visible}
          className="absolute top-[var(--ds-space-1)] right-[var(--ds-space-1)]"
          label={visible ? '隐藏密码' : '显示密码'}
          size="sm"
          type="button"
          variant="ghost"
          onClick={() => setVisible((current) => !current)}
        >
          {visible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
        </IconButton>
      </div>
      {capsLock ? (
        <FieldDescription id={capsLockId} role="status" className="text-warning">
          大写锁定已开启
        </FieldDescription>
      ) : null}
    </div>
  );
}
