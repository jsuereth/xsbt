package sbt

	import complete.Parser
	import java.io.File

/** A concrete settings system that uses `sbt.Scope` for the scope type. */
object Def extends Init[Scope] with TaskMacroExtra
{
	type Classpath = Seq[Attributed[File]]

	val triggeredBy = AttributeKey[Seq[Task[_]]]("triggered-by")
	val runBefore = AttributeKey[Seq[Task[_]]]("run-before")
	private[sbt] val parseResult: TaskKey[Any] = TaskKey("$parse-result", "Internal: used to implement input tasks.", KeyRanks.Invisible)
	val resolvedScoped = SettingKey[ScopedKey[_]]("resolved-scoped", "The ScopedKey for the referencing setting or task.", KeyRanks.DSetting)

	lazy val showFullKey: Show[ScopedKey[_]] = showFullKey(None)
	def showFullKey(keyNameColor: Option[String]): Show[ScopedKey[_]] =
		new Show[ScopedKey[_]] { def apply(key: ScopedKey[_]) = displayFull(key, keyNameColor) }

	def showRelativeKey(current: ProjectRef, multi: Boolean, keyNameColor: Option[String] = None): Show[ScopedKey[_]] = new Show[ScopedKey[_]] {
		def apply(key: ScopedKey[_]) =
			Scope.display(key.scope, colored(key.key.label, keyNameColor), ref => displayRelative(current, multi, ref))
	}
	def displayRelative(current: ProjectRef, multi: Boolean, project: Reference): String = project match {
		case BuildRef(current.build) => "{.}/"
		case `current` => if(multi) current.project + "/" else ""
		case ProjectRef(current.build, x) => x + "/"
		case _ => Reference.display(project) + "/"
	}
	def displayFull(scoped: ScopedKey[_]): String = displayFull(scoped, None)
	def displayFull(scoped: ScopedKey[_], keyNameColor: Option[String]): String = Scope.display(scoped.scope, colored(scoped.key.label, keyNameColor))
	def displayMasked(scoped: ScopedKey[_], mask: ScopeMask): String = Scope.displayMasked(scoped.scope, scoped.key.label, mask)

	def colored(s: String, color: Option[String]): String = color match {
		case Some(c) => c + s + scala.Console.RESET
		case None => s
	}

	/** A default Parser for splitting input into space-separated arguments.
	* `argLabel` is an optional, fixed label shown for an argument during tab completion.*/
	def spaceDelimited(argLabel: String = "<arg>"): Parser[Seq[String]] = complete.Parsers.spaceDelimited(argLabel)	

	/** Lifts the result of a setting initialization into a Task. */
	def toITask[T](i: Initialize[T]): Initialize[Task[T]] = map(i)(std.TaskExtra.inlineTask)

		import language.experimental.macros
		import std.TaskMacro.{InitParserInput, inputTaskMacroImpl, inputTaskDynMacroImpl, MacroValue, taskDynMacroImpl, taskMacroImpl, StateParserInput}
		import std.SettingMacro.{settingDynMacroImpl,settingMacroImpl}

	def task[T](t: T): Def.Initialize[Task[T]] = macro taskMacroImpl[T]
	def taskDyn[T](t: Def.Initialize[Task[T]]): Def.Initialize[Task[T]] = macro taskDynMacroImpl[T]
	def setting[T](t: T): Def.Initialize[T] = macro settingMacroImpl[T]
	def settingDyn[T](t: Def.Initialize[T]): Def.Initialize[T] = macro settingDynMacroImpl[T]
	def inputTask[T](t: T): Def.Initialize[InputTask[T]] = macro inputTaskMacroImpl[T]
	def inputTaskDyn[T](t: Def.Initialize[Task[T]]): Def.Initialize[InputTask[T]] = macro inputTaskDynMacroImpl[T]

	// The following conversions enable the types Initialize[T], Initialize[Task[T]], and Task[T] to
	//  be used in task and setting macros as inputs with an ultimate result of type T

	implicit def macroValueI[T](in: Initialize[T]): MacroValue[T] = ???
	implicit def macroValueIT[T](in: Initialize[Task[T]]): MacroValue[T] = ???

	// The following conversions enable the types Parser[T], Initialize[Parser[T]], and Initialize[State => Parser[T]] to
	//  be used in the inputTask macro as an input with an ultimate result of type T
	implicit def parserInitToInput[T](p: Initialize[Parser[T]]): InitParserInput[T] = ???
	implicit def parserInitStateToInput[T](p: Initialize[State => Parser[T]]): StateParserInput[T] = ???
}
// these need to be mixed into the sbt package object because the target doesn't involve Initialize or anything in Def
trait TaskMacroExtra 
{
	implicit def macroValueT[T](in: Task[T]): std.TaskMacro.MacroValue[T] = ???
	implicit def parserToInput[T](in: Parser[T]): std.TaskMacro.RawParserInput[T] = ???
}