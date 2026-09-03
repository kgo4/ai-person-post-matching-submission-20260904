/**
 * 岗位管理相关类型定义
 */

export interface PostPost {
  id: number
  postCode: string
  postName: string
  jobDescription: string
  templateId: number
  status: number
  createdTime: string
  abilityRequirements?: PostAbilityModel[]
}

export type PostCreateDTO = Omit<PostPost, 'postCode'> & {
  postCode?: string
}

export interface PostAbilityModel {
  id: number
  postId: number
  tagId: number | null
  abilityName?: string | null
  techStack?: string | null
  tagName?: string
  minRequiredLevel: number
  weight: number
  isRequired: number
  isCore: number
  remark: string
}

export interface PostHardConditionRule {
  id?: number
  postId: number
  fieldName: string
  fieldLabel: string
  fieldType?: string
  operator: string
  expectedValue: string
  valueRankJson?: string
  enabled?: number
  sortOrder?: number
  remark?: string
}

export interface PostHardConditionRuleDTO {
  id?: number
  postId: number
  fieldName: string
  fieldLabel: string
  fieldType?: string
  operator: string
  expectedValue: string
  valueRankJson?: string
  enabled?: number
  sortOrder?: number
  remark?: string
}

export interface PostAbilityModelConfigDTO {
  id?: number
  postId: number
  tagId?: number | null
  abilityName?: string | null
  techStack?: string | null
  minRequiredLevel: number
  weight: number
  isRequired?: number
  isCore?: number
  remark?: string
}

export interface PostAbilityModelVO {
  postId: number
  postName: string
  postCode: string
  abilityRequirements: PostAbilityDetailVO[]
}

export interface PostAbilityDetailVO {
  modelId: number
  tagId: number | null
  abilityName?: string | null
  techStack: string
  tagName: string
  minRequiredLevel: number
  weight: number
  isRequired: number
  isCore: number
}

export interface PostModelTemplate {
  id: number
  templateCode: string
  templateName: string
  postSequence: string
  description: string
  status: number
}

export interface PostTemplateSaveDTO {
  id?: number
  templateCode: string
  templateName: string
  postSequence: string
  description?: string
}

export type PostTemplateCreateDTO = Omit<PostTemplateSaveDTO, 'templateCode'> & {
  templateCode?: string
}

export interface TemplateAbilityModel {
  id?: number
  templateId: number
  tagId: number
  minRequiredLevel: number
  weight: number
  isRequired: number
  isCore: number
  remark?: string
}

export interface JdAbilityItem {
  suggestedName: string
  tagCategory: string
  minRequiredLevel: number
  weight: number
  isCore: number
  isRequired: number
  reasoning: string
  matchStatus: 'MATCHED' | 'SIMILAR' | 'NEW'
  matchedTagId?: number
  matchedTagName?: string
  similarityScore?: number
}

export interface JdAnalyzeResponse {
  taskId: number
  postId: number
  postName: string
  jobSummary: string
  abilities: JdAbilityItem[]
  analysisStatus: number
  errorMessage?: string
}
