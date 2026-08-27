import { Eye, EyeOff } from 'lucide-react';
import { useState } from 'react';

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
    <div>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <div className="relative mt-1">
        <input
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
          className="border-input bg-background focus-visible:ring-ring h-10 w-full rounded-md border px-3 pr-24 outline-none focus-visible:ring-2"
        />
        <button
          type="button"
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
          className="text-muted-foreground hover:text-foreground focus-visible:ring-ring absolute inset-y-0 right-1 my-auto flex h-8 items-center gap-1 rounded px-2 text-xs outline-none focus-visible:ring-2"
        >
          {visible ? <EyeOff aria-hidden="true" size={15} /> : <Eye aria-hidden="true" size={15} />}
          {visible ? '隐藏密码' : '显示密码'}
        </button>
      </div>
      {capsLock ? (
        <p id={capsLockId} role="status" className="text-warning mt-1 text-xs">
          大写锁定已开启
        </p>
      ) : null}
    </div>
  );
}
