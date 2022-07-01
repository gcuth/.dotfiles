" Generic (HIINRN)
set hls ic is nu rnu noswf

set nocompatible
filetype off

"  -------
"  PLUGINS
"  (managed via junegunn/vim-plug)
"  -------

if empty(glob('~/.vim/autoload/plug.vim'))
    silent !curl -fLo ~/.vim/autoload/plug.vim --create-dirs
        \ https://raw.githubusercontent.com/junegunn/vim-plug/master/plug.vim
    autocmd VimEnter * PlugInstall --sync | source $MYVIMRC
endif

call plug#begin()

Plug 'SirVer/ultisnips' " snippet engine of choice
Plug 'honza/vim-snippets' " default snippets
" Plug 'neoclide/coc.nvim', {'branch': 'release'} " code completion engine to live in the modern world
" Plug 'vim-airline/vim-airline' " a nicer statusline
" Plug 'vim-airline/vim-airline-themes' " a nicer set of statusline themes
" Plug 'tpope/vim-commentary' " sensible commenting
" Plug 'tpope/vim-surround' " sensible paren management
" Plug 'vim-scripts/paredit.vim', { 'for': 'clojure' } " paren slurping and burping
" Plug 'tpope/vim-fireplace', { 'for': 'clojure' } " a quasi-repl for the world's greatest programming language
" Plug 'kien/rainbow_parentheses.vim', { 'for': 'clojure' } " sensible paren colouring
Plug 'junegunn/goyo.vim' " a (*dry-retch*) 'distraction-free' writing environment
Plug 'junegunn/limelight.vim' " ... that greys all non-focus grafs
" Plug 'junegunn/fzf', { 'do': { -> fzf#install() } }
" Plug 'junegunn/fzf.vim'
" Plug 'jalvesaq/Nvim-R', {'branch': 'stable'} " R
" Plug 'github/copilot.vim'

call plug#end()

set backspace=2 "make backspace work like other text editors

set visualbell "flash screen in error instead of sounding a beep

filetype plugin indent on
filetype plugin on

set autoread
set hidden

" Tabs
set tabstop=4 " show existing tab with 4 spaces width
set shiftwidth=4 " when indenting with '>', use 4 spaces width
set expandtab " On pressing tab, insert 4 spaces

" Key mappings
" arrow keys do nothing, because you should be living the hjkl life
noremap <Up> <NOP>
noremap <Down> <NOP>
noremap <Left> <NOP>
noremap <Right> <NOP>
" leader key that makes sense for dvorak
let mapleader = ","
" fzf file picker to ctrl-p
nnoremap <C-P> :FZF<CR>

" Colours
syntax enable
highlight LineNr ctermfg=darkgrey
highlight Search ctermbg=white
highlight Search ctermfg=black

" A statusline theme
let g:airline_theme = 'minimalist'

" Color name (:help cterm-colors) or ANSI code
let g:limelight_conceal_ctermfg = 'gray'
let g:limelight_conceal_ctermfg = 240

" Color name (:help gui-colors) or RGB color
let g:limelight_conceal_guifg = 'DarkGray'
let g:limelight_conceal_guifg = '#777777'

" Default 0.5
let g:limelight_default_coefficient = 0.7

" wrap long lines
set wrap
" ruler can be useful
set ruler
" show incomplete commands
set showcmd

" never make backups (e.g. file~)
set nobackup

" limit number of open tabs to 100
set tabpagemax=100

set clipboard^=unnamed

" Snippet hotkeys
let g:UltiSnipsExpandTrigger="<tab>"
let g:UltiSnipsJumpForwardTrigger="<tab>"
let g:UltiSnipsJumpBackwardTrigger="<s-tab>"
let g:snips_author="Galen"
let g:snips_author_email="g@galen.me"

" save files on InsertLeave
autocmd InsertLeave * :w!
autocmd InsertLeave * :execute 'silent !tmux refresh-client -S &' | redraw!

" Writing Mode for Markdown files
au BufNewFile,BufRead *.{md,markdown,Rmd} call WritingMode()

function! WritingMode()
    set linebreak
    Goyo
    Limelight
endfunction

function! CodingMode()
    match Error /\%81v.\+/ " Match lines longer than 80 char as errors
endfunction

au BufNewFile,BufRead *.{py,R} call CodingMode()

" Clojure Mode for clj
au BufNewFile,BufRead *.{clj,cljs,cljc,cljx} call ClojureMode()

function! ClojureMode()
    RainbowParenthesesToggle
    RainbowParenthesesLoadRound
    RainbowParenthesesLoadSquare
    RainbowParenthesesLoadBraces
    call CodingMode()
endfunction

" COC Interfacing
" inoremap <silent><expr> <TAB>
"       \ pumvisible() ? "\<C-n>" :
"       \ <SID>check_back_space() ? "\<TAB>" :
"       \ coc#refresh()
" inoremap <expr><S-TAB> pumvisible() ? "\<C-p>" : "\<C-h>"

" function! s:check_back_space() abort
"   let col = col('.') - 1
"   return !col || getline('.')[col - 1]  =~# '\s'
" endfunction
