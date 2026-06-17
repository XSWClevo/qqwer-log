import request from '@/utils/request'

export interface JobCommunicationOverview {
  todayCommunicated: number
  todayReplied: number
  weekCommunicated: number
  weekReplied: number
  biweekCommunicated: number
  biweekReplied: number
}

export interface JobCommunicationTrendItem {
  date: string
  communicated: number
  replied: number
  replyRate: number
}

export interface JobCommunicationPageRequest {
  pageNum?: number
  pageSize?: number
  platform?: string
  status?: string
  startTime?: string
  endTime?: string
  keyword?: string
}

export function getJobCommunicationOverview() {
  return request<JobCommunicationOverview>({
    url: '/api/job-communications/overview',
    method: 'GET'
  })
}

export function getJobCommunicationTrend(granularity = 'day') {
  return request<JobCommunicationTrendItem[]>({
    url: '/api/job-communications/trend',
    method: 'GET',
    params: { granularity }
  })
}

export function getJobCommunicationMarketAnalysis() {
  return request<Record<string, Array<{ name: string; count: number }>>>({
    url: '/api/job-communications/market-analysis',
    method: 'GET'
  })
}

export function getJobCommunicationPage(data: JobCommunicationPageRequest) {
  return request<any>({
    url: '/api/job-communications/page',
    method: 'POST',
    data
  })
}
