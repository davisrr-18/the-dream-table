# Redstone Dreams

Alpha `1.0.0-alpha` para Minecraft **1.21.1** com Fabric Loader e Fabric API.

A Dream Table escolhe uma farm clássica, mostra o holograma no mundo e constrói um bloco por segundo depois que os materiais são entregues.

## Como jogar

A mesa fica na aba Redstone do criativo, ou nesta receita:

```
LLL
PLP
OOO
```

Lápis-lazúli na fileira de cima e no centro, papel nas laterais e tábuas de carvalho embaixo.

1. Coloque a mesa e escolha a farm.
2. Saia do menu. Um marcador amarelo segue a mira.
3. Clique direito no chão para fixar o holograma. Clique de novo para mudar o lugar.
4. **R** gira a farm. A tecla pode ser trocada em Controles, categoria Redstone Dreams.
5. Segure o material e clique direito na prancha para entregar. A linha é riscada quando fecha a conta.
6. Volte à mesa e clique em Construir. A posição trava e a obra anda um bloco por segundo.

Farms desta alpha: cana, bambu, plantação, farm de mobs e farm de ferro. O terreno precisa estar livre. Se um bloco impedir a obra, ele fica vermelho no holograma.

Descartar devolve os materiais e tira o holograma.

Em **Meus esquemas**, a mesa lê arquivos `.nbt`, `.schem` e `.litematic` nestas pastas da instância:

- `schematics`
- `config/worldedit/schematics`
- `config/litematica`
- `config/redstone_dreams/structures`
- `schematics` dentro da pasta do mundo

## Requisitos

- Minecraft 1.21.1
- Fabric Loader 0.16 ou mais novo
- Fabric API
- Java 21

## Compilar

Na raiz do projeto, com Java 21:

```
./gradlew build
```

O arquivo do mod fica em `build/libs/redstone_dreams-1.0.0-alpha.jar`. Os jars com sufixo `sources` e `dev` são só para desenvolvimento.
