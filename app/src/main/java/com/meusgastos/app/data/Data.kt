package com.meusgastos.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

object Tipo {
    const val DESPESA = "despesa"
    const val RECEITA = "receita"
    const val APORTE = "aporte"
}

/** Um lançamento: despesa, recebimento ou aporte em previdência. Data no formato yyyy-MM-dd. */
@Entity(tableName = "lancamentos", indices = [Index("data")])
data class Lancamento(
    @PrimaryKey val id: String,
    val tipo: String,
    val valor: Double,
    val data: String,
    val categoria: String? = null,
    val planoId: String? = null,
    val descricao: String = "",
    val pagamento: String? = null,
    val tributavel: Boolean = false,
    val criadoEm: String = "",
    val grupo: String? = null,
)

/** Categoria de despesa (com orçamento mensal opcional) ou de recebimento. */
@Entity(tableName = "categorias")
data class Categoria(
    @PrimaryKey val id: String,
    val tipo: String,
    val nome: String,
    val icone: String,
    val cor: String,
    val orcamento: Double = 0.0,
    val ordem: Int = 0,
)

/** Plano de previdência privada (PGBL, VGBL, fundo de pensão). */
@Entity(tableName = "planos")
data class Plano(
    @PrimaryKey val id: String,
    val nome: String,
    val tipo: String,
    val instituicao: String = "",
    val saldo: Double = 0.0,
    val saldoData: String = "",
)

@Entity(tableName = "pagamentos")
data class FormaPagamento(
    @PrimaryKey val nome: String,
    val ordem: Int = 0,
)

@Dao
interface AppDao {
    // Lançamentos
    @Query("SELECT * FROM lancamentos ORDER BY data DESC, criadoEm DESC")
    fun lancamentos(): Flow<List<Lancamento>>

    @Query("SELECT * FROM lancamentos ORDER BY data, criadoEm")
    suspend fun todosLancamentos(): List<Lancamento>

    @Upsert suspend fun salvar(l: Lancamento)
    @Upsert suspend fun salvarTodos(l: List<Lancamento>)

    @Query("DELETE FROM lancamentos WHERE id = :id")
    suspend fun excluirLancamento(id: String)

    @Query("UPDATE lancamentos SET categoria = NULL WHERE categoria = :catId")
    suspend fun limparCategoria(catId: String)

    @Query("UPDATE lancamentos SET planoId = NULL WHERE planoId = :planoId")
    suspend fun limparPlano(planoId: String)

    // Categorias
    @Query("SELECT * FROM categorias ORDER BY ordem, nome")
    fun categorias(): Flow<List<Categoria>>

    @Query("SELECT * FROM categorias ORDER BY ordem, nome")
    suspend fun todasCategorias(): List<Categoria>

    @Query("SELECT COUNT(*) FROM categorias")
    suspend fun contarCategorias(): Int

    @Upsert suspend fun salvarCategoria(c: Categoria)
    @Upsert suspend fun salvarCategorias(c: List<Categoria>)

    @Query("DELETE FROM categorias WHERE id = :id")
    suspend fun excluirCategoria(id: String)

    @Query("DELETE FROM categorias")
    suspend fun limparCategorias()

    // Planos
    @Query("SELECT * FROM planos ORDER BY nome")
    fun planos(): Flow<List<Plano>>

    @Query("SELECT * FROM planos ORDER BY nome")
    suspend fun todosPlanos(): List<Plano>

    @Upsert suspend fun salvarPlano(p: Plano)

    @Query("DELETE FROM planos WHERE id = :id")
    suspend fun excluirPlano(id: String)

    @Query("DELETE FROM planos")
    suspend fun limparPlanos()

    // Formas de pagamento
    @Query("SELECT * FROM pagamentos ORDER BY ordem, nome")
    fun pagamentos(): Flow<List<FormaPagamento>>

    @Query("SELECT * FROM pagamentos ORDER BY ordem, nome")
    suspend fun todosPagamentos(): List<FormaPagamento>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirPagamento(p: FormaPagamento)

    @Query("DELETE FROM pagamentos WHERE nome = :nome")
    suspend fun excluirPagamento(nome: String)

    @Query("DELETE FROM pagamentos")
    suspend fun limparPagamentos()
}

@Database(
    entities = [Lancamento::class, Categoria::class, Plano::class, FormaPagamento::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "meusgastos.db")
                    .build().also { instance = it }
            }
    }
}
