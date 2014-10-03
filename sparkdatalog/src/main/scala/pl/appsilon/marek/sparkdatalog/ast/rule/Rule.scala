package pl.appsilon.marek.sparkdatalog.ast.rule

import pl.appsilon.marek.sparkdatalog.util.{NTimed, Timed}
import pl.appsilon.marek.sparkdatalog.{Valuation, Database, Relation}
import pl.appsilon.marek.sparkdatalog.ast.SemanticException
import pl.appsilon.marek.sparkdatalog.eval.{RelationInstance, StateShard, StaticEvaluationContext}

case class Rule(head: Head, body: RuleBody) {


  /** Semantic analysis */
  val notBoundHeadVariables = head.args.toSet -- body.outVariables
  if (notBoundHeadVariables.nonEmpty)
    throw new SemanticException("Unbound variable(s) in rule head: " +
      notBoundHeadVariables.mkString(", ") +
      " (head has free variables: " + head.args.mkString(", ") +
      "; but positive variables in body are: " + body.outVariables.mkString(", ") + ")")
  val variableIds: Map[String, Int] = body.outVariables.toSeq.zipWithIndex.toMap
  val analyzedBodies = body.analyze(variableIds)

  def evaluate(context: StaticEvaluationContext, shard: StateShard): Seq[(Long, RelationInstance)] = {
    val solutions: Seq[Valuation] = NTimed("findSolutions_"+head, () => analyzedBodies.flatMap(_.findSolutions(context, shard)))

    val generatedRelations = NTimed("emitSolutions_"+head, () =>head.emitSolutions(solutions, variableIds))
    //println("evaluate shard = " + " \n\n\t -> " + generatedRelations)
    generatedRelations.toKeyValue
  }

}