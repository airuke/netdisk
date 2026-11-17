import { computed, markRaw, ref } from 'vue'
import { defineStore } from 'pinia'
import { uploadFile } from '@/api/files'
import { uploadConfig, listUploadSessions, checkInstant, createUpload, getUpload,
  cancelUpload, uploadPart, completeUpload } from '@/api/uploads'
import { getToken } from '@/utils/token'
import { hashFile, delay, abortError } from '@/utils/hash'
import { readTasks, saveTasks } from '@/utils/uploadPersistence'

const working = ['hashing', 'checking', 'creating', 'uploading', 'verifying', 'merging']
export const useUploadsStore = defineStore('uploads', () => {
  const tasks = ref([]), open = ref(false), revision = ref(0)
  const recoveryError = ref(''), persistenceWarning = ref(''), syncing = ref(false), ready = ref(false)
  const config = ref({ chunkSize: 8 * 1024 * 1024, maxFileSize: 5 * 1024 ** 3, sessionTtlHours: 72 })
  const activeCount = computed(() => tasks.value.filter(t => t.status === 'queued' || working.includes(t.status)).length)
  const running = new Map(), controllers = new Map()
  let generation = 0, userId = '', initializePromise = null
  function persist() { persistenceWarning.value = saveTasks(userId, tasks.value) }
  function finished(task, session) {
    task.status = 'done'; task.progress = 100; task.hashProgress = 100
    task.instant = Boolean(session?.instant); task.error = ''; task.file = null; task.desired = null
    if (session) applySession(task, session)
    task.status = 'done'; revision.value++; persist()
  }
  function applySession(task, s) {
    task.sessionId = s.id; task.clientId = s.clientId; task.name = s.name; task.size = s.size
    task.hash = s.sha256; task.parentId = s.parentId; task.folderName = s.folderName
    task.chunkCount = s.chunkCount; task.partCount = s.status === 'COMPLETED' && !s.instant ? s.chunkCount : s.parts.length; task.expiresAt = s.expiresAt
    task.instant = s.instant
    if (s.status !== 'COMPLETED') task.progress = Math.min(99, Math.round(s.parts.reduce((sum, p) => sum + p.size, 0) / Math.max(s.size, 1) * 100))
  }
  async function sync() {
    if (!userId || syncing.value) return
    const current = generation
    syncing.value = true; recoveryError.value = ''
    try {
      const sessions = await listUploadSessions()
      if (current !== generation) return
      for (const s of sessions) {
        let task = tasks.value.find(t => t.clientId === s.clientId)
        if (task && (running.has(task.id) || task.cancelPending || task.status === 'queued')) continue
        if (!task && s.status === 'COMPLETED') continue
        if (!task) {
          task = { id: crypto.randomUUID(), clientId: s.clientId, file: null, verifiedFile: false,
            status: 'needs-file', createdAt: Date.now(), hashProgress: 0, speed: 0, error: '', progress: 0 }
          tasks.value.push(task)
        }
        applySession(task, s)
        if (s.status === 'COMPLETED') finished(task, s)
        else if (s.status === 'MERGING') task.status = 'merging'
        else task.status = task.file ? 'paused' : 'needs-file'
      }
      persist()
    } catch (e) { if (current === generation) recoveryError.value = e.message }
    finally { if (current === generation) syncing.value = false }
  }
  async function initialize(id) {
    id = String(id)
    if (userId === id && ready.value) return
    if (userId === id && initializePromise) return initializePromise
    reset(); userId = id; tasks.value = readTasks(id)
    const current = generation
    initializePromise = (async () => {
      try {
        const settings = await uploadConfig()
        if (current !== generation) return
        config.value = settings
        await sync()
      } catch (e) { if (current === generation) recoveryError.value = e.message }
      finally { if (current === generation) { ready.value = true; initializePromise = null } }
    })()
    return initializePromise
  }
  function add(files, parentId, folderName) {
    if (!userId || !ready.value || !getToken()) throw new Error('正在读取账号，请稍后选择文件')
    const selected = Array.from(files)
    const unfinished = tasks.value.filter(t => !['done', 'cancelled'].includes(t.status)).length
    if (unfinished + selected.length > 100)
      throw new Error(`当前还可添加 ${Math.max(0, 100 - unfinished)} 项任务，请减少文件数量或取消旧任务`)
    if (!selected.length) return
    for (const file of selected) {
      const tooLarge = file.size > config.value.maxFileSize
      const id = crypto.randomUUID()
      tasks.value.push({ id, clientId: id, sessionId: null, file: markRaw(file), verifiedFile: false,
        name: file.name, size: file.size, parentId, folderName, hash: '', hashProgress: 0,
        status: tooLarge ? 'failed' : 'queued', progress: 0, speed: 0, partCount: 0, chunkCount: 0,
        error: tooLarge ? '文件超过当前上传大小限制' : '', retryable: !tooLarge, createdAt: Date.now() })
    }
    open.value = true; persist(); pump()
  }
  function pump() {
    if (!userId || !getToken()) return
    for (const task of tasks.value.filter(t => t.status === 'queued' && t.file && !running.has(t.id))
      .slice(0, Math.max(0, 2 - running.size))) {
      const promise = run(task)
      running.set(task.id, promise)
    }
  }
  async function transfer(task, session, controller, assertLive) {
    const size = session.chunkSize, stored = new Set(session.parts.map(p => p.index))
    const queue = Array.from({ length: session.chunkCount }, (_, i) => i).filter(i => !stored.has(i))
    const inflight = new Map()
    let storedBytes = session.parts.reduce((sum, p) => sum + p.size, 0), next = 0, failure
    const started = performance.now(), baseline = storedBytes
    function progress() {
      assertLive()
      const bytes = storedBytes + [...inflight.values()].reduce((a, b) => a + b, 0)
      task.progress = Math.min(99, Math.round(bytes / Math.max(task.size, 1) * 100))
      task.speed = Math.max(0, bytes - baseline) / Math.max(0.1, (performance.now() - started) / 1000)
    }
    async function worker() {
      while (next < queue.length) {
        assertLive()
        const index = queue[next++], blob = task.file.slice(index * size, Math.min((index + 1) * size, task.size))
        const hash = await hashFile(blob, controller.signal)
        for (let attempt = 0; ; attempt++) {
          assertLive(); inflight.set(index, 0)
          try {
            await uploadPart(session.id, index, blob, hash, controller.signal, event => {
              if (controller.signal.aborted) return
              inflight.set(index, Math.min(blob.size, Math.round(event.loaded / (event.total || blob.size || 1) * blob.size)))
              progress()
            })
            assertLive(); inflight.delete(index); storedBytes += blob.size; task.partCount++
            progress(); persist(); break
          } catch (e) {
            inflight.delete(index)
            if (controller.signal.aborted || attempt >= 3 || ![0, 409, 429, 502, 503, 504].includes(e.status ?? 0)) throw e
            await delay(500 * 2 ** attempt, controller.signal)
          }
        }
      }
    }
    const workers = Array.from({ length: Math.min(2, queue.length) }, () => worker().catch(e => {
      if (!failure) failure = e
      controller.abort(); throw e
    }))
    await Promise.allSettled(workers)
    if (failure) throw failure
    assertLive()
  }
  async function run(task) {
    const current = generation, token = getToken(), runId = task.id
    const controller = new AbortController(); controllers.set(runId, controller)
    task.desired = null; task.error = ''; task.restartRequired = false
    function assertLive() {
      if (current !== generation || controller.signal.aborted || token !== getToken()
        || ['paused', 'cancelled'].includes(task.desired)) throw abortError()
    }
    try {
      if (!task.file) { task.status = 'needs-file'; return }
      if (!task.verifiedFile) {
        task.status = 'hashing'; task.hashProgress = 0
        const hash = await hashFile(task.file, controller.signal, p => { if (!controller.signal.aborted) task.hashProgress = p })
        assertLive()
        if (task.hash && task.hash !== hash) {
          task.file = null; task.status = 'needs-file'; task.error = '所选文件的内容不一致，请重新选择原文件'; return
        }
        task.hash = hash; task.verifiedFile = true; persist()
      }
      const body = { clientId: task.clientId, name: task.name, parentId: task.parentId, size: task.size, sha256: task.hash }
      let session
      if (!task.sessionId) {
        task.status = 'checking'
        const result = await checkInstant(body, controller.signal); assertLive()
        if (result.instant) { finished(task, result.session); return }
        if (task.size === 0) {
          task.status = 'verifying'
          await uploadFile(task.file, task.parentId, controller.signal, () => {}, task.hash)
          assertLive(); finished(task); return
        }
        task.status = 'creating'; session = await createUpload(body, controller.signal)
      } else { task.status = 'checking'; session = await getUpload(task.sessionId, controller.signal) }
      assertLive(); applySession(task, session); persist()
      while (session.status === 'MERGING') {
        task.status = 'merging'; task.progress = 99
        await delay(1500, controller.signal)
        session = await getUpload(session.id, controller.signal); assertLive()
      }
      if (session.status === 'COMPLETED') { finished(task, session); return }
      if (session.status !== 'OPEN') { const e = new Error('任务已取消或过期，请重新上传'); e.status = 410; throw e }
      task.status = 'uploading'
      await transfer(task, session, controller, assertLive)
      task.status = 'merging'; task.progress = 99; persist()
      const completed = await completeUpload(session.id, controller.signal)
      assertLive(); finished(task, completed)
    } catch (e) {
      if (current !== generation || task.status === 'done') return
      if (task.desired === 'cancelled') task.status = 'cancelled'
      else if (task.desired === 'paused') task.status = task.file ? 'paused' : 'needs-file'
      else { task.status = 'failed'; task.error = e.message; task.restartRequired = [404, 410].includes(e.status) }
    } finally {
      if (current === generation) {
        controllers.delete(runId); running.delete(runId); task.stopping = false; persist(); pump()
      }
    }
  }
  function pause(task) {
    task.desired = 'paused'; task.stopping = running.has(task.id); controllers.get(task.id)?.abort()
    task.status = task.file ? 'paused' : 'needs-file'; task.speed = 0; persist(); pump()
  }
  function resume(task) {
    if (running.has(task.id) || task.cancelPending || task.retryable === false) return
    if (!task.file) { task.status = 'needs-file'; persist(); return }
    if (task.restartRequired) {
      task.clientId = crypto.randomUUID(); task.sessionId = null; task.progress = 0; task.partCount = 0
    }
    task.status = 'queued'; task.error = ''; task.desired = null; persist(); pump()
  }
  function attachFile(task, file) {
    if (running.has(task.id) || task.cancelPending) return
    if (file.size !== task.size) { task.error = '所选文件大小不同，请选择原文件'; return }
    task.file = markRaw(file); task.verifiedFile = false; resume(task)
  }
  async function cancel(task) {
    if (task.cancelPending || task.status === 'done') return
    const current = generation
    task.cancelPending = true; task.desired = 'cancelled'; controllers.get(task.id)?.abort()
    task.status = 'cancelled'; persist()
    try {
      const session = await cancelUpload(task.clientId)
      if (current !== generation) return
      if (session?.status === 'COMPLETED') finished(task, session)
      else { task.status = 'cancelled'; task.file = null; task.error = ''; revision.value++; persist() }
    } catch (e) {
      if (current === generation) { task.status = 'failed'; task.error = `取消未成功：${e.message}`; task.desired = 'paused' }
    } finally { if (current === generation) { task.cancelPending = false; persist(); pump() } }
  }
  function clearCompleted() {
    tasks.value = tasks.value.filter(t => !['done', 'cancelled'].includes(t.status) || t.cancelPending || running.has(t.id))
    persist()
  }
  function reset() {
    generation++; controllers.forEach(c => c.abort()); controllers.clear(); running.clear()
    for (const task of tasks.value) {
      task.file = null; task.verifiedFile = false
      if (!['done', 'cancelled'].includes(task.status)) task.status = 'needs-file'
    }
    persist(); tasks.value = []; userId = ''; open.value = false; ready.value = false
    syncing.value = false; initializePromise = null; recoveryError.value = ''; persistenceWarning.value = ''
  }
  return { tasks, open, revision, config, activeCount, recoveryError, persistenceWarning, syncing, ready,
    initialize, sync, add, pause, resume, retry: resume, attachFile, cancel, clearCompleted, reset }
})
