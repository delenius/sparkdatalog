package pl.appsilon.marek.sparkdatalog.ast.rule

import org.apache.spark.rdd.RDD
import pl.appsilon.marek.sparkdatalog
import pl.appsilon.marek.sparkdatalog.{Database, Valuation}
import pl.appsilon.marek.sparkdatalog.ast.SemanticException
import pl.appsilon.marek.sparkdatalog.ast.subgoal.{NonrelationalSubgoalsTopologicalSort, GoalPredicate, Subgoal, SubgoalsTopologicalSort}
import pl.appsilon.marek.sparkdatalog.eval.{RelationInstance, StateShard, State, StaticEvaluationContext}

import scala.collection.immutable.IndexedSeq

case class RuleBody(subgoals: Seq[Subgoal]) {

  /** Semantic analysis */

  val hasRelationalSubgoal = subgoals.exists(isRelational)
  if(!hasRelationalSubgoal) throw new SemanticException("Rule must contain at least one relational subgoal.")

  val (_, outVariables) = SubgoalsTopologicalSort(subgoals)
  private val (relationalSubgoals: Seq[Subgoal], nonRelationalSubgoals: Seq[Subgoal]) = subgoals.partition(isRelational)
  private val relationalSubgoalsRotations: IndexedSeq[Seq[Subgoal]] = relationalSubgoals.indices.map(pos => {
    val (nose, tail) = relationalSubgoals.splitAt(pos)
    tail ++ nose
  })

  def fillWithNonrelational(relationalSubgoalsRotated: Seq[Subgoal]): Seq[Subgoal] =
    NonrelationalSubgoalsTopologicalSort(relationalSubgoalsRotated, nonRelationalSubgoals)

  private val relationalSubgoalsRotationsWithNonrelationals: IndexedSeq[Seq[Subgoal]] = relationalSubgoalsRotations.map(fillWithNonrelational)


  def isRelational: (Subgoal) => Boolean = {
    case GoalPredicate(_) => true
    case _ => false
  }

  def analyze(variableIds: Map[String, Int]): Seq[AnalyzedRuleBody] = {
    for(sortedSubgoals <- relationalSubgoalsRotationsWithNonrelationals) yield {
      val boundVariables = sortedSubgoals.scanLeft(Set[Int]())((acc, subgoal) => acc ++ subgoal.getOutVariables.map(variableIds)).dropRight(1)
      val analyzedSubgoals = sortedSubgoals.zip(boundVariables).map(subgoalWithBinding =>
        subgoalWithBinding._1.analyze(variableIds, subgoalWithBinding._2))
      AnalyzedRuleBody(analyzedSubgoals, Valuation(variableIds.size))
    }
  }
}