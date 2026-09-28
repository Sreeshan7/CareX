import { Link } from 'react-router-dom'
import type { TeamCalendar } from '../../api/types'
import { cn } from '../../lib/utils'

const COLORS: Record<string, string> = { ANNUAL: 'bg-brand-600', CASUAL: 'bg-violet-500', SICK: 'bg-orange-500' }

/** Members × days grid; approved = solid, pending = striped; red day header = above absence threshold. No calendar library. */
export function TeamCalendarGrid({ data }: { data: TeamCalendar }) {
  const days: string[] = []
  for (let d = new Date(`${data.from}T00:00:00`); d <= new Date(`${data.to}T00:00:00`); d.setDate(d.getDate() + 1)) {
    days.push(new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10))
  }
  return (
    <div className="space-y-8">
      {data.teams.map((team) => {
        const members = data.members.filter((m) => m.teamId === team.id)
        const dayInfo = data.daysByTeam[String(team.id)] ?? []
        return (
          <div key={team.id}>
            <p className="mb-2 text-sm font-semibold text-slate-700">{team.name} <span className="font-normal text-slate-500">· {team.size} · {team.thresholdPct}% / min {team.minAbsent}</span></p>
            <div className="overflow-x-auto rounded-xl border border-slate-200">
              <table className="border-collapse text-xs">
                <thead>
                  <tr>
                    <th className="sticky left-0 z-10 min-w-[140px] bg-white px-3 py-2 text-left font-medium text-slate-500">&nbsp;</th>
                    {days.map((d) => {
                      const info = dayInfo.find((x) => x.date === d)
                      const date = new Date(`${d}T00:00:00`)
                      return (
                        <th key={d} className={cn('min-w-[34px] px-1 py-1.5 text-center font-medium',
                          info?.over ? 'bg-red-100 text-red-700' : info && !info.workingDay ? 'bg-slate-50 text-slate-400' : 'text-slate-600')}
                          title={info ? `${info.absent}/${info.teamSize} absent` : undefined}>
                          <div className="text-[10px] uppercase">{date.toLocaleDateString('en-IN', { weekday: 'narrow' })}</div>
                          <div>{date.getDate()}</div>
                        </th>
                      )
                    })}
                  </tr>
                </thead>
                <tbody>
                  {members.map((m) => (
                    <tr key={m.id} className="border-t border-slate-100">
                      <td className="sticky left-0 z-10 whitespace-nowrap bg-white px-3 py-1.5 font-medium text-slate-700">{m.name}</td>
                      {days.map((d) => {
                        const e = data.entries.find((x) => x.employeeId === m.id && x.startDate <= d && x.endDate >= d)
                        const info = dayInfo.find((x) => x.date === d)
                        return (
                          <td key={d} className={cn('h-8 p-0.5', info && !info.workingDay && 'bg-slate-50')}>
                            {e && info?.workingDay && (
                              <Link to={`/requests/${e.requestId}`} title={`#${e.requestId} ${e.leaveTypeCode} · ${e.status}`}
                                className={cn('block h-full w-full rounded', COLORS[e.leaveTypeCode] ?? 'bg-slate-400',
                                  e.status !== 'APPROVED' && 'hatched opacity-70', e.flagged && 'ring-2 ring-orange-400')} />
                            )}
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )
      })}
    </div>
  )
}
