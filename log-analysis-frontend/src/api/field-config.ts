import request from '@/utils/request'

export interface FieldConfigRequest {
  configType: string
  selectedFields: string[]
  fieldOrder: string[]
}

export interface FieldConfigResponse {
  configType: string
  selectedFields: string[]
  fieldOrder: string[]
  availableFields: string[]
}

/**
 * 固定图表配置
 */
export interface PinnedChartConfig {
  id: string
  name: string
  label: string
  type: 'bar' | 'line'
}

export interface PinnedChartsConfigRequest {
  configType: string
  pinnedCharts: PinnedChartConfig[]
}

export interface PinnedChartsConfigResponse {
  configType: string
  pinnedCharts: PinnedChartConfig[]
}

/**
 * 获取用户字段配置
 */
export const getFieldConfig = async (username: string, configType: string): Promise<FieldConfigResponse> => {
  const response = await request.get(`/api/field-config/${configType}`, {
    params: { username }
  })
  return response.data
}

/**
 * 保存用户字段配置
 */
export const saveFieldConfig = async (username: string, config: FieldConfigRequest): Promise<void> => {
  await request.post(`/api/field-config`, config, {
    params: { username }
  })
}

/**
 * 重置用户字段配置
 */
export const resetFieldConfig = async (username: string, configType: string): Promise<void> => {
  await request.delete(`/api/field-config/${configType}`, {
    params: { username }
  })
}

/**
 * 获取用户固定图表配置
 * 使用现有的字段配置API，将pinnedCharts序列化为JSON字符串存储在selectedFields中
 */
export const getPinnedChartsConfig = async (username: string): Promise<PinnedChartsConfigResponse> => {
  try {
    const response = await request.get(`/api/field-config/pinned_charts`, {
      params: { username }
    })
    const data = response.data
    if (data && data.selectedFields && data.selectedFields.length > 0) {
      // selectedFields 中存储的是序列化的 pinnedCharts JSON 字符串数组
      const pinnedCharts: PinnedChartConfig[] = data.selectedFields.map((jsonStr: string) => {
        try {
          return JSON.parse(jsonStr)
        } catch {
          return null
        }
      }).filter(Boolean)
      return { configType: 'pinned_charts', pinnedCharts }
    }
    return { configType: 'pinned_charts', pinnedCharts: [] }
  } catch (error) {
    // 如果配置不存在，返回空配置
    return { configType: 'pinned_charts', pinnedCharts: [] }
  }
}

/**
 * 保存用户固定图表配置
 * 将pinnedCharts序列化为JSON字符串数组存储
 */
export const savePinnedChartsConfig = async (username: string, pinnedCharts: PinnedChartConfig[]): Promise<void> => {
  // 将每个 pinnedChart 序列化为 JSON 字符串
  const selectedFields = pinnedCharts.map(chart => JSON.stringify(chart))
  await request.post(`/api/field-config`, {
    configType: 'pinned_charts',
    selectedFields,
    fieldOrder: selectedFields
  }, {
    params: { username }
  })
}
