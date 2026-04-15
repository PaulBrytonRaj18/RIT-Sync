import { apiClient } from './client'
import { cacheCourses } from '../offline/db'

export const coursesApi = {
  getMyCourses: async () => {
    const res = await apiClient.get('/courses/my')
    const courses = res.data.data
    await cacheCourses(courses)
    return courses
  },

  createCourse: async (data) => {
    const res = await apiClient.post('/courses', data)
    return res.data.data
  },

  enrollStudent: async (courseId, studentId) => {
    const res = await apiClient.post(`/courses/${courseId}/enroll/${studentId}`)
    return res.data
  },

  getById: async (id) => {
    const res = await apiClient.get(`/courses/${id}`)
    return res.data.data
  },
}
