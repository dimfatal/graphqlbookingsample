package com.booking.core.utils

import com.booking.core.domain.Interval

import java.time.LocalDate

object SuggestionLogic {

  def findFreeIntervals(busy: List[Interval], start: LocalDate, finish: LocalDate): List[Interval] = {
    if (!start.isBefore(finish)) return Nil
    val busySorted = busy.sortBy(_.begin)

    @annotation.tailrec
    def loop(remaining: List[Interval], cursor: LocalDate, acc: List[Interval]): List[Interval] = remaining match {
      case Nil =>
        if (cursor.isBefore(finish)) acc :+ Interval(cursor, finish) else acc

      case interval :: rest =>
        val s = if (interval.begin.isBefore(start)) start else interval.begin
        val e = if (interval.end.isAfter(finish)) finish else interval.end
        if (!s.isBefore(e)) loop(rest, cursor, acc)
        else if (cursor.isBefore(s)) {
          val acc2       = acc :+ Interval(cursor, s)
          val nextCursor = if (e.isAfter(s)) e else s
          loop(rest, nextCursor, acc2)
        } else {
          val nextCursor = if (e.isAfter(cursor)) e else cursor
          loop(rest, nextCursor, acc)
        }
    }

    loop(busySorted, start, Nil)
  }

  def earliestFutureSuggestion(availableDates: List[Interval], anchor: LocalDate, duration: Int): Option[Interval] =
    availableDates
      .find { case Interval(s, e) =>
        val start = if (anchor.isAfter(s)) anchor else s
        !start.plusDays(duration.toLong).isAfter(e)
      }
      .map { case Interval(s, _) =>
        val start = if (anchor.isAfter(s)) anchor else s
        Interval(start, start.plusDays(duration.toLong))
      }

  def latestPast(free: List[Interval], today: LocalDate, endLimit: LocalDate, duration: Int): Option[Interval] =
    free.foldLeft(Option.empty[Interval]) { (latest, interval) =>
      val s = if (interval.begin.isAfter(today)) interval.begin else today
      val e = if (interval.end.isBefore(endLimit)) interval.end else endLimit
      if (s.isBefore(e)) {
        val start0 = e.minusDays(duration.toLong)
        val start  = if (start0.isAfter(s)) start0 else s
        val end    = start.plusDays(duration.toLong)
        if (!end.isAfter(e)) Some(Interval(start, end)) else latest
      } else latest
    }
}