export type FormErrors<T extends Record<string, unknown>> = Partial<
  Record<keyof T, string>
>
