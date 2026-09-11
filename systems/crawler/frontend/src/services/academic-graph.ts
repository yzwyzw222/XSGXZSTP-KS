import { api } from '@/services/api'
import { withQuery } from '@/services/business'
import type { GraphResponse } from '@/types/api'

export type WorkCategory = 'PAPER' | 'PATENT' | 'MASTER_THESIS' | 'DOCTORAL_THESIS'
export type AcademicGraphMode = 'relations' | 'achievements' | 'background'

export interface AuthorGraphQuery {
  authorId: number
  category?: WorkCategory
  collaborationsOnly: boolean
  chronological: boolean
  page: number
  size: number
}

export interface AuthorGraphResponse {
  graph: GraphResponse
  page: number
  size: number
  totalWorks: number
}

export const academicGraphApi = {
  load: ({ authorId, ...query }: AuthorGraphQuery, signal: AbortSignal) =>
    api.get<unknown>(withQuery(`/api/v1/graph/authors/${authorId}`, query), { signal }),
}
