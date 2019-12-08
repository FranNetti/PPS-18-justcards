package org.justcards.server.session_manager

import org.justcards.commons.Card
import org.justcards.server.Commons.{PlayerCards, UserInfo}
import org.justcards.server.knowledge_engine.game_knowledge.GameKnowledge

import scala.util.Random

trait GameBoard {

  /**
   * Getter
   * @return cards on field
   */
  def fieldCards: List[(Card, UserInfo)]

  /**
   * Getter
   * @return cards of all player
   */
  def playerCards: Map[UserInfo, PlayerCards]

  /**
   * Getter
   * @return turn order of all player
   */
  def turn: List[UserInfo]

  /**
   * Getter
   * @return last card of deck
   */
  def optionLastCardDeck: Option[Card]

  /**
   * Getter
   * @return User that must play
   */
  def optionTurnPlayer: Option[UserInfo]

  /**
   * Getter
   * @return player after given player
   */
  def playerAfter(player: UserInfo): Option[UserInfo]

  /**
   * Getter
   * @param player player
   * @return cards in the player hand
   */
  def handCardsOf(player: UserInfo): Set[Card]

  /**
   * Getter
   * @return cards in the hand of player have to play
   */
  def handCardsOfTurnPlayer: Option[Set[Card]] = this optionTurnPlayer match {
    case Some(player) => Some(this handCardsOf player)
    case None => None
  }

  /**
   * Getter
   * @param player player
   * @return cards that player won
   */
  def tookCardsOf(player: UserInfo): Set[Card]

  /**
   * Players draw
   * @return GameBoard after the players draw
   */
  def draw: Option[GameBoard]

  /**
   * Player play a card
   * @param card card that player play
   * @return GameBoard after player play the card
   */
  def playerPlays(card: Card): GameBoard

  /**
   * Give fieldCards to turn winner and reset turn
   * @param player turn winner
   * @return GameBoard with empty field and reset turn
   */
  def handWinner(player: UserInfo): GameBoard

}

object GameBoard {

  def apply(gameKnowledge: GameKnowledge, team1: List[UserInfo], team2: List[UserInfo], firstPlayer: Option[UserInfo]): GameBoard = {
    import ListWithShift._
    val initialConfiguration = gameKnowledge.initialConfiguration//hand, draw, field
    var deck: List[Card] = Random.shuffle (gameKnowledge.deckCards toList)
    val turn: List[UserInfo] = team1.zipAll(team2, null, null)
      .flatMap {
        case (a, null) => Seq(a)
        case (null, b) => Seq(b)
        case (a, b) => Seq(a, b)
      }
    val players: Map[UserInfo, PlayerCards] = turn.map( a => {
      val player = (a , PlayerCards(deck take initialConfiguration._1 toSet, Set()))
      deck = deck drop initialConfiguration._1
      player
    }).toMap
    val handTurn: List[UserInfo] = if (firstPlayer isEmpty) {
      val first = gameKnowledge.sessionStarterPlayer(players.map(x => (x._1, x._2.hand)).toSet)
      if (first isDefined)
        (turn shiftTo first.get) get
      else
        turn
    } else (turn shiftTo firstPlayer.get) get

    GameBoardImpl(
      deck take initialConfiguration._3 map(a=>(a,handTurn.head)),
      players,
      deck drop initialConfiguration._3,
      handTurn,
      turn,
      initialConfiguration._2)
  }



  private[this] case class GameBoardImpl(fieldCards: List[(Card, UserInfo)],
                                         playerCards: Map[UserInfo, PlayerCards],
                                         deck: List[Card],
                                         handTurn: List[UserInfo],
                                         turn: List[UserInfo],
                                         nCardsDraw: Int) extends GameBoard {
    import ListWithShift._

    private def apply(field: List[(Card,UserInfo)],
              playerCards: Map[UserInfo, PlayerCards],
              deck: List[Card],
              handTurn: List[UserInfo]): GameBoard = {
      GameBoardImpl(field, playerCards, deck, handTurn, turn, nCardsDraw)
    }

    override def optionLastCardDeck: Option[Card] = deck lastOption

    override def optionTurnPlayer: Option[UserInfo] = handTurn headOption

    override def playerAfter(player: UserInfo): Option[UserInfo] = turn indexOf player match {
      case -1 => None
      case x if x == turn.size - 1 => Some(turn.head)
      case x => Some(turn(x + 1))
    }

    override def handCardsOf(player: UserInfo): Set[Card] = playerCards(player).hand

    override def tookCardsOf(player: UserInfo): Set[Card] = playerCards(player).took

    override def draw: Option[GameBoard] = {
      if (deck isEmpty) None
      else {
        var tmpDeck: List[Card] = deck
        Some(this(
          fieldCards,
          playerCards.mapValues( cards => {
            val result = PlayerCards(cards.hand ++ tmpDeck.take(nCardsDraw), cards.took)
            tmpDeck = tmpDeck drop nCardsDraw
            result
          }),
          tmpDeck,
          handTurn
        ))
      }
    }


    override def playerPlays(card: Card): GameBoard =
      this(
        fieldCards :+ (card, handTurn.head),
        playerCards.map{
          case (player, cards) if player == handTurn.head => handTurn.head -> PlayerCards(cards.hand - card, cards.took)
          case x => x
        },
        deck,
        handTurn tail
      )

    override def handWinner(winner: UserInfo): GameBoard =
      this(
        List(),
        playerCards.map{
          case (`winner`, cards) => winner -> PlayerCards(cards.hand, cards.took ++ fieldCards.map(_._1))
          case x => x
        },
        deck,
        turn.shiftTo(winner).get
      )

  }

}

