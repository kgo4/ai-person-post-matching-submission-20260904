/**
 * 动态表单/表格渲染工具
 * 用于根据后端配置的扩展字段动态生成表单和表格
 */

// 扩展字段类型定义（补充自动生成类型）
export interface ExtendFieldConfig {
  fieldKey: string
  fieldName: string
  fieldType: ExtendFieldType
  required: boolean
  options?: FieldOption[]
  defaultValue?: any
  placeholder?: string
  rules?: FieldRule[]
  order?: number
  visible?: boolean
  editable?: boolean
}

export type ExtendFieldType =
  | 'text'
  | 'number'
  | 'select'
  | 'multi-select'
  | 'date'
  | 'datetime'
  | 'textarea'
  | 'switch'
  | 'radio'
  | 'checkbox'
  | 'cascader'
  | 'upload'

export interface FieldOption {
  label: string
  value: string | number
  children?: FieldOption[]
}

export interface FieldRule {
  type: 'required' | 'min' | 'max' | 'pattern' | 'custom'
  value?: any
  message: string
  trigger?: 'blur' | 'change'
}

/**
 * 将扩展字段配置转换为Element Plus表单项配置
 * @param fields 扩展字段配置列表
 * @returns 表单项配置
 */
export function buildFormItems(fields: ExtendFieldConfig[]) {
  return fields
    .filter((f) => f.visible !== false)
    .sort((a, b) => (a.order || 0) - (b.order || 0))
    .map((field) => ({
      prop: field.fieldKey,
      label: field.fieldName,
      type: mapFieldType(field.fieldType),
      required: field.required,
      placeholder: field.placeholder || `请输入${field.fieldName}`,
      options: field.options || [],
      rules: buildFieldRules(field),
      span: getSpanByType(field.fieldType),
    }))
}

/**
 * 将扩展字段配置转换为表格列配置
 * @param fields 扩展字段配置列表
 * @returns 表格列配置
 */
export function buildTableColumns(fields: ExtendFieldConfig[]) {
  return fields
    .filter((f) => f.visible !== false)
    .sort((a, b) => (a.order || 0) - (b.order || 0))
    .map((field) => ({
      prop: field.fieldKey,
      label: field.fieldName,
      type: mapTableColumnType(field.fieldType),
      width: getWidthByType(field.fieldType),
      formatter: buildFormatter(field),
      options: field.options || [],
    }))
}

/**
 * 构建初始表单数据
 * @param fields 扩展字段配置列表
 * @returns 初始表单数据对象
 */
export function buildFormData(fields: ExtendFieldConfig[]): Record<string, any> {
  const formData: Record<string, any> = {}

  fields.forEach((field) => {
    if (field.defaultValue !== undefined) {
      formData[field.fieldKey] = field.defaultValue
    } else {
      formData[field.fieldKey] = getDefaultByType(field.fieldType)
    }
  })

  return formData
}

/**
 * 构建表单校验规则
 * @param fields 扩展字段配置列表
 * @returns 校验规则对象
 */
export function buildFormRules(fields: ExtendFieldConfig[]): Record<string, any[]> {
  const rules: Record<string, any[]> = {}

  fields.forEach((field) => {
    const fieldRules = buildFieldRules(field)
    if (fieldRules.length > 0) {
      rules[field.fieldKey] = fieldRules
    }
  })

  return rules
}

// ========== 内部辅助函数 ==========

function mapFieldType(fieldType: ExtendFieldType): string {
  const typeMap: Record<ExtendFieldType, string> = {
    text: 'input',
    number: 'input-number',
    select: 'select',
    'multi-select': 'select',
    date: 'date-picker',
    datetime: 'date-picker',
    textarea: 'input',
    switch: 'switch',
    radio: 'radio-group',
    checkbox: 'checkbox-group',
    cascader: 'cascader',
    upload: 'upload',
  }
  return typeMap[fieldType] || 'input'
}

function mapTableColumnType(fieldType: ExtendFieldType): string {
  const typeMap: Record<ExtendFieldType, string> = {
    'multi-select': 'tags',
    switch: 'switch',
    upload: 'image',
    date: 'date',
    datetime: 'datetime',
  } as any
  return (typeMap[fieldType] as string) || 'default'
}

function buildFieldRules(field: ExtendFieldConfig): any[] {
  const rules: any[] = []

  if (field.required) {
    rules.push({
      required: true,
      message: `${field.fieldName}不能为空`,
      trigger: 'blur',
    })
  }

  if (field.rules) {
    field.rules.forEach((rule) => {
      const r: any = {
        message: rule.message,
        trigger: rule.trigger || 'blur',
      }

      switch (rule.type) {
        case 'min':
          r.min = rule.value
          break
        case 'max':
          r.max = rule.value
          break
        case 'pattern':
          r.pattern = new RegExp(rule.value)
          break
        case 'custom':
          r.validator = rule.value
          break
      }

      rules.push(r)
    })
  }

  return rules
}

function getSpanByType(fieldType: ExtendFieldType): number {
  if (['textarea'].includes(fieldType)) return 24
  if (['cascader'].includes(fieldType)) return 12
  return 12
}

function getWidthByType(fieldType: ExtendFieldType): string | undefined {
  if (fieldType === 'switch') return '100px'
  if (fieldType === 'datetime') return '180px'
  if (fieldType === 'date') return '120px'
  return undefined
}

function getDefaultByType(fieldType: ExtendFieldType): any {
  const defaults: Record<ExtendFieldType, any> = {
    text: '',
    number: 0,
    select: '',
    'multi-select': [],
    date: '',
    datetime: '',
    textarea: '',
    switch: false,
    radio: '',
    checkbox: [],
    cascader: [],
    upload: '',
  }
  return defaults[fieldType] ?? ''
}

function buildFormatter(field: ExtendFieldConfig): ((row: any) => string) | undefined {
  if (!field.options || field.options.length === 0) return undefined

  if (field.fieldType === 'multi-select') {
    return (row: any) => {
      const values = row[field.fieldKey] || []
      return values
        .map((v: any) => field.options?.find((o) => o.value === v)?.label || v)
        .join(', ')
    }
  }

  if (['select', 'radio', 'cascader'].includes(field.fieldType)) {
    return (row: any) => {
      return field.options?.find((o) => o.value === row[field.fieldKey])?.label || row[field.fieldKey]
    }
  }

  return undefined
}
